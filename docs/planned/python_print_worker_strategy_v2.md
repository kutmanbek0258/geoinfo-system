# Стратегия реализации печатного воркера (Вариант Б)

Настоящий документ определяет технический регламент по переносу рендеринга и компоновки печатных отчетов (PDF) из Java в новый специализированный Python-контейнер **`geoprint-worker`**, сохраняя **`geoprint-service`** как легкий планировщик/оркестратор задач.

---

## 1. Схема взаимодействия и очереди Kafka

```
[Vue.js Client]
       │  (1. HTTP: POST /api/print/tasks)
       ▼
[geoprint-service (Java)] ──► Сохраняет задачу в DB (Status: PENDING)
       │
       │  (2. Отправка события воркеру)
       ▼
   [Kafka: geo.print.tasks] (Сообщение: { "taskId": "UUID" })
       │
       ▼
[geoprint-worker (Python)]
       │  (3. Считывает задачу, ставит статус PROCESSING)
       │  (4. Рендерит карту Matplotlib + Собирает PDF ReportLab)
       │  (5. Загружает готовый PDF в MinIO S3 bucket: 'documents' / 'reports')
       │
       │  (6. Отправка события об успехе / ошибке)
       ▼
   [Kafka: geo.print.results] (Сообщение: { "taskId": "UUID", "status": "COMPLETED"/"FAILED", "s3Key": "reports/id.pdf", "errorMessage": "..." })
       │
       ▼
[geoprint-service (Java)] ──► Обновляет задачу в DB (Status: COMPLETED / FAILED)
```

---

## 2. Пошаговый план реализации

### Шаг 1: Облегчение `geoprint-service` (Java)
1. **Удаление тяжелых зависимостей в `pom.xml`:**
   * Полностью удалить секции зависимостей `org.geotools` (gt-main, gt-xml, gt-render, gt-epsg-hsql и др.) и `openhtmltopdf` (openhtmltopdf-core, openhtmltopdf-pdfbox).
   * Это уменьшит размер артефакта сборки, ускорит запуск сервиса и избавит от необходимости скачивать тяжелые maven-репозитории OSGeo.
2. **Удаление классов рендеринга:**
   * Удалить неиспользуемые классы `MapRenderer.java` и `PdfBuilder.java`.
3. **Обновление логики `PrintOrchestrator.java`:**
   * Метод `processPrintTask(UUID taskId)` больше не должен выполнять локальный рендеринг и сохранение. 
   * Он переводится в режим ожидания: при старте обновляет статус на `PROCESSING` и просто завершает выполнение транзакции. 
   * Реальная работа будет переложена на Python-воркер.
4. **Добавление нового слушателя в `PrintKafkaConsumer.java`:**
   * Слушать топик результатов `geo.print.results` (groupId: `geoprint-service-group`).
   * При получении сообщения `{ "taskId": "...", "status": "COMPLETED", "s3Key": "..." }`:
     * Обновить статус задачи на `COMPLETED`.
     * Сгенерировать presigned URL через `minioService` для ключа `s3Key` и записать в поле `s3Url`.
   * При получении `"status": "FAILED"`:
     * Записать `errorMessage` и перевести задачу в статус `FAILED`.

---

### Шаг 2: Создание нового контейнера `geoprint-worker` (Python)
1. **Структура директории `geoprint-worker/`:**
   ```
   [geoprint-worker/]
   ├── Dockerfile
   ├── requirements.txt
   ├── main.py                    # Инициализация консюмера Kafka
   └── app/
       ├── __init__.py
       ├── core/
       │   ├── __init__.py
       │   ├── config.py          # Конфигурация брокеров, MinIO
       │   └── clients.py         # Настройка MinIO клиента
       └── processors/
           ├── __init__.py
           ├── print_manager.py   # Оркестратор шагов (Загрузка -> Рендеринг -> PDF -> S3)
           ├── map_renderer.py    # Matplotlib + Rasterio + GeoPandas рендеринг карты в PNG
           └── layout_builder.py  # ReportLab Platypus сборка отчета в PDF
   ```
2. **Базовый образ Docker:**
   Использовать `FROM osgeo/gdal:ubuntu-full-3.6.3` для быстрого кэширования слоев (по аналогии с `geoabstract-worker`). Воркеру необходим GDAL для корректной работы `rasterio` и `cartopy`/`shapely`.
3. **Файл зависимостей `requirements.txt`:**
   ```
   kafka-python==2.0.2
   minio==7.1.15
   reportlab==4.1.0
   matplotlib==3.8.3
   rasterio==1.3.9
   geopandas==0.14.3
   shapely==2.0.3
   pyproj==3.6.1
   ```

---

### Шаг 3: Разработка логики Python-воркера
1. **`map_renderer.py`:**
   * Получает BBox, список слоев (COG URL из MinIO, GeoJSON для векторов), DPI и параметры стилей.
   * Отрисовывает растровые подложки с помощью `rasterio` и векторы с помощью `geopandas`.
   * Применяет цвета обводки/заливки и прозрачность, переданные в свойствах геометрии.
   * Выводит сетку координат (Gridlines).
   * Сохраняет во временный `/tmp/map.png`.
2. **`layout_builder.py`:**
   * Инициализирует шаблон ReportLab (`SimpleDocTemplate`) нужного формата (А4-А0).
   * Регистрирует TTF-шрифт с поддержкой кириллицы (например, DejaVuSans), чтобы избежать пустых квадратов в PDF.
   * Верстает штамп чертежа (Таблица с исполнителем, проектом, датой, масштабом) и легенду.
   * Объединяет карту и штамп на холсте Platypus.
3. **`print_manager.py`:**
   * Получает задачу из Kafka, запрашивает спецификацию печати из БД `print_tasks` или считывает ее из тела сообщения.
   * Скачивает исходные файлы векторов/растров.
   * Запускает рендеринг и сборку PDF.
   * Загружает готовый файл в бакет `documents` (ключ `reports/{taskId}.pdf`).
   * Публикует сообщение об успехе или ошибке в `geo.print.results`.

---

### Шаг 4: Обновление Docker Compose
В корневой файл `docker-compose.yml` добавить описание нового сервиса:
```yaml
  geoprint-worker:
    build:
      context: geoprint-worker
    container_name: geoprint-worker
    environment:
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}
      MINIO_ENDPOINT: ${MINIO_ENDPOINT}
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
    depends_on:
      - kafka
      - minio
```

---

## 3. Критические аспекты и проверка корректности

1. **Сохранение работоспособности UI:**
   Фронтенд по-прежнему будет видеть эндпоинт `/api/print/tasks` и запрашивать статус. Мы лишь заменим внутренний механизм генерации.
2. **Обработка кириллицы:**
   Использование встроенного шрифта с поддержкой utf-8 обязательное требование для рендеринга названий проектов и штампов на русском языке.
3. **Тестовый запуск:**
   Мы проведем компиляцию Java-сервиса без зависимостей GeoTools и проверим, что он успешно запускается, после чего создадим скелет нового Python воркера.
