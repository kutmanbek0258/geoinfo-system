## **Стратегия интеграции модуля генерации печатных отчетов (ReportLab + Matplotlib) в geoabstract-worker**

Настоящий документ определяет архитектурную стратегию и технический регламент по переносу функционала высокоточного рендеринга картографических отчетов (вплоть до формата А0) из Java-окружения (`GeoTools`) в существующий Python-воркер (`geoabstract-worker`).

Выбранный стек **Matplotlib (геопространственный холст) + ReportLab (компоновщик документов)** обеспечивает максимальную производительность, жесткий контроль над выделением памяти в `tmpfs` и бесшовную интеграцию с cloud-native форматами данных платформы.

---

### **1. Архитектурная декомпозиция и структура модуля**

В соответствии с принципом разделения ответственности (Core/Plugins), новый функционал реализуется в виде изолированного плагина печати. Логика разделяется на два независимых слоя:

1. **Слой рендеринга картографического холста (Matplotlib + Cartopy + Rasterio):** отвечает за точное позиционирование растровых подложек (COG), наложение векторных слоев (GeoJSON) и отрисовку картографической сетки в заданном масштабе и проекции.
2. **Слой печатной компоновки (ReportLab Platypus):** отвечает за формирование структуры PDF-документа: рамки, штампы, таблицы метаданных, условные обозначения (легенда) и масштабирование элементов под целевой формат бумаги (A4–A0).

```
[app/plugins/geo_print/]
├── __init__.py
├── print_plugin.py          # Точка входа, наследуемая от GeoWorkerPlugin
├── map_renderer.py          # Генерация картографического PNG высокого разрешения (Matplotlib)
├── layout_builder.py        # Сборка финального PDF (ReportLab Platypus)
└── styles_parser.py         # Конвертер внутренних JSON-стилей в параметры Matplotlib

```

---

### **2. Конвейер обработки задачи (Пайплайн исполнения)**

Процесс обработки задачи в воркере полностью асинхронен и состоит из следующих шагов:

```
[Kafka: geo.print.tasks] 
       │
       ▼
1. Скачивание COG из MinIO & Векторов из DB ──► В RAM-диск (tmpfs)
       │
       ▼
2. Matplotlib: Рендеринг карты (BBox, DPI)   ──► Временный высокоточный PNG
       │
       ▼
3. ReportLab: Сборка PDF (Шаблон, Штамп)      ──► Итоговый PDF-файл в tmpfs
       │
       ▼
4. Загрузка в MinIO & Отправка статуса        ──► [Kafka: geo.print.results]

```

#### **Детальное описание шагов:**

1. **Потребление задачи:** Воркер считывает из топика `geo.print.tasks` спецификацию отчета: `projectId`, `bbox` (координатный охват), `dpi`, `paperFormat` (A0-A4), `orientation` (альбомная/книжная) и структуру слоев.
2. **Локализация данных:** Растровые слои подкачиваются из MinIO напрямую как Cloud Optimized GeoTIFF. Векторные слои передаются в виде GeoJSON-массивов. Все операции чтения-записи происходят внутри изолированной директории `workspace` на RAM-диске (`tmpfs`).
3. **Генерация карты:** Модуль `map_renderer.py` инициализирует объект `matplotlib.pyplot.figure` без вывода на экран (интерфейс `Agg`). Пространственный охват жестко фиксируется через границы `BBox`.
4. **Компоновка отчета:** Временное изображение карты передается в `layout_builder.py`. С помощью ReportLab Platypus (`SimpleDocTemplate`, `Paragraph`, `Table`, `Image`) формируется финальный лист: рассчитываются отступы, генерируется экспликация и штамп чертежа.
5. **Публикация:** Готовый PDF загружается в MinIO, временные файлы в `tmpfs` уничтожаются.

---

### **3. Техническая реализация ключевых модулей**

#### **3.1. Рендеринг карты (`map_renderer.py`)**

Для работы с геопространственными координатами Matplotlib дополняется библиотекой `Cartopy`. Это исключает искажения при печати больших территорий.

```python
import matplotlib
matplotlib.use('Agg')  # Отключение GUI-потока
import matplotlib.pyplot as plt
import cartopy.crs as ccrs
import rasterio
from rasterio.plot import show
import geopandas as gpd

def render_map_canvas(bbox: list, rasters: list, vectors: list, target_path: str, dpi: int):
    # Определение проекции (например, Web Mercator для соответствия карте)
    crs = ccrs.WebMercator()
    
    # Расчет физического размера фигуры в дюймах на основе требуемого DPI
    fig, ax = plt.subplots(figsize=(11.69, 16.54), subplot_kw={'projection': crs}, dpi=dpi) # Пример для A3
    ax.set_extent([bbox[0], bbox[2], bbox[1], bbox[3]], crs=crs)
    
    # 1. Рендеринг растров через Rasterio
    for raster_url in rasters:
        with rasterio.open(raster_url) as src:
            show(src, ax=ax, cmap='viridis', alpha=0.8)
            
    # 2. Рендеринг векторов через GeoPandas
    for vec_data in vectors:
        gdf = gpd.read_file(vec_data)
        if gdf.crs != 'EPSG:3857':
            gdf = gdf.to_crs(epsg=3857)
        gdf.plot(ax=ax, edgecolor='black', facecolor='none', linewidth=1.5)
        
    # Добавление картографической сетки координат
    ax.gridlines(draw_labels=True, dms=True, x_inline=False, y_inline=False)
    
    plt.savefig(target_path, bbox_inches='tight', pad_inches=0)
    plt.close(fig)

```

#### **3.2. Компоновка PDF (`layout_builder.py`)**

ReportLab использует систему координат в пунктах (1 дюйм = 72 pt). Модуль преобразует миллиметры стандартов ISO в пункты и собирает документ с использованием сетки `Platypus`.

```python
from reportlab.lib.pagesizes import A3, landscape
from reportlab.platypus import SimpleDocTemplate, Paragraph, Table, TableStyle, Image, Spacer
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib import colors

def build_pdf_report(map_image_path: str, output_pdf_path: str, metadata: dict):
    # Инициализация документа целевого формата
    doc = SimpleDocTemplate(
        output_pdf_path,
        pagesize=landscape(A3),
        rightMargin=36, leftMargin=36, topMargin=36, bottomMargin=36
    )
    
    story = []
    styles = getSampleStyleSheet()
    
    # Заголовок документа
    title = Paragraph(f"<b>КАРТОГРАФИЧЕСКИЙ ОТЧЕТ: {metadata['projectName']}</b>", styles['Title'])
    story.append(title)
    story.append(Spacer(1, 15))
    
    # Интеграция отрендеренной карты (масштабирование под границы Platypus)
    # Физический размер карты на листе: ~1000x650 pt
    map_img = Image(map_image_path, width=1000, height=650)
    story.append(map_img)
    story.append(Spacer(1, 15))
    
    # Формирование штампа (основной надписи) в виде таблицы ReportLab
    stamp_data = [
        ['Наименование проекта:', metadata['projectName'], 'Исполнитель:', 'Воркер геоаналитики'],
        ['Дата генерации:', metadata['date'], 'Система координат:', 'EPSG:3857 (Web Mercator)']
    ]
    
    stamp_table = Table(stamp_data, colWidths=[150, 350, 150, 350])
    stamp_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (0,1), colors.lightgrey),
        ('BACKGROUND', (2,0), (2,1), colors.lightgrey),
        ('GRID', (0,0), (-1,-1), 1, colors.black),
        ('FONTNAME', (0,0), (-1,-1), 'Helvetica'), # Требуется регистрация TrueType шрифта с поддержкой кириллицы
        ('FONTSIZE', (0,0), (-1,-1), 10),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
    ]))
    
    story.append(stamp_table)
    
    doc.build(story)

```

---

### **4. Управление памятью и оптимизация ресурсов в `tmpfs**`

Рендеринг больших форматов (А1, А0) при полиграфическом разрешении (300 DPI) может приводить к пиковым нагрузкам на оперативную память. Стратегия контроля включает три обязательных правила:

1. **Принудительное закрытие дескрипторов Matplotlib:** Каждая фигура должна быть явно уничтожена с помощью `plt.close(fig)` и `plt.clf()`. Python не всегда оперативно освобождает память canvas, если объект остался в контексте. После вызова компоновщика ReportLab необходимо принудительно вызывать `gc.collect()`.
2. **Стриминг растров:** Использование `rasterio` позволяет читать не весь тяжелый GeoTIFF целиком в память, а только окно данных (`Window`), попадающее в целевой `BBox` печати.
3. **Лимитирование разрешения:** На уровне схемы параметров плагина жестко ограничивается максимальный DPI для сверхбольших форматов:
* Для A4–A3: доступно 150 и 300 DPI.
* Для A1–A0: по умолчанию выставляется 150 DPI. Переключение на 300 DPI блокируется, если размер исходных растров превышает 300 МБ, во избежание OOM (Out of Memory).



---

### **5. Симулятор координатной сетки и компоновки листа**

При работе с ReportLab и Matplotlib критически важно понимать, как физические размеры бумаги соотносятся с пиксельным разрешением карты и точками (points) ReportLab. Ниже представлен интерактивный симулятор, позволяющий рассчитать параметры координатной сетки отчета перед запуском пайплайна.

---

### **6. План миграции и шаги по замене `geoprint-service**`

1. **Регистрация шрифтов с поддержкой кириллицы:** По умолчанию ReportLab поддерживает ограниченный набор шрифтов (Helvetica, Times-Roman), которые не содержат кириллических символов. В ядро воркера необходимо добавить TrueType шрифт (например, `DejaVuSans.ttf`) и регистрировать его при инициализации плагина:
```python
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
pdfmetrics.registerFont(TTFont('DejaVuSans', 'path/to/DejaVuSans.ttf'))

```


2. **Обновление контракта JSON-схемы:** Внедрить схему плагина печати в общий реестр динамического UI фронтенда (согласно общей стратегии перехода к Schema-Driven UI). Входы должны запрашивать массив растров, массив векторов и параметры листа.
3. **Параллельный запуск и приемочные тесты:** Запустить функционал в тестовом режиме на топике `geo.print.tasks.v2`. Сравнить идентичность геометрических пропорций отчетов из старого Java-сервиса и нового Python-модуля.
4. **Вывод Java-сервиса из Docker Compose:** После подтверждения стабильности рендеринга больших форматов, удалить контейнер `geoprint-service` и его изолированную базу данных `print_db`, полностью переведя нагрузку на горизонтально масштабируемые Python-воркеры.