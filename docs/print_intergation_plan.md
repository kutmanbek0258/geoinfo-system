Ниже представлен финальный архитектурно-технический документ в формате Markdown, готовый к сохранению в репозиторий вашего проекта как техническое руководство по интеграции модуля печати (`PRINT_INTEGRATION.md`).

---

```markdown
# 🖨️ Спецификация интеграции модуля печати и экспорта отчетов (geo-print-service)

Настоящий документ описывает архитектурные решения, протоколы взаимодействия, структуру данных и шаги по реализации изолированного микросервиса печати графических карт высокого разрешения и сопутствующих документов в формате PDF.

---

## 1. Архитектурный обзор и Компоненты

Модуль печати реализуется как независимый сервис в рамках общей микросервисной архитектуры системы, взаимодействующий с остальными компонентами асинхронно через брокер сообщений Kafka.


```

┌────────────────────────┐
│  Браузер (Vue.js)      │◄──────────────────────────────┐
└───────────┬────────────┘                              │
│ 1. POST /print/tasks                      │ 5. WebSocket
▼                                           │    Notification
┌────────────────────────┐                              │
│      nginx-proxy       │                              │
└───────────┬────────────┘                              │
│                                           │
▼                                           │
┌────────────────────────┐                      ┌───────┴────────┐
│   geo-print-service    ├─────────────────────►│ Gateway/WS     │
└─────┬────────────▲─────┘                      └────────────────┘
│            │
│ 2. Emit    │ 3. Consume Task
▼            │
┌─────┴────────────┴─────┐      ┌────────────┐
│   Kafka (KRaft Mode)   │      │ GeoServer  │◄── (Тайлы WMS/WMTS)
└────────────────────────┘      └─────▲──────┘
│
┌────────────────────────┐            │ 4. Рендеринг движком
│   MinIO Storage (S3)   │◄───────────┘    GeoTools + PDFBox
└────────────────────────┘

```

### Ключевые архитектурные решения:
1. **Изоляция нагрузки:** Выделенный микросервис `geo-print-service` предотвращает падение основных сервисов геоданных из-за утечек памяти (OOM) или высокой нагрузки на CPU при рендеринге тяжелой графики (300+ DPI).
2. **Программная сборка (Pure Java):** Отказ от сторонних контейнеров компоновки отчетов (например, Mapfish Print) в пользу связки библиотек **GeoTools** и **OpenHTMLtoPDF** для обеспечения 100% контроля над кодом и гибкой кастомизации штампов/таблиц.
3. **Асинхронный пайплайн:** Длительные операции сборки карты и компиляции PDF обрабатываются через очереди Kafka, исключая HTTP-таймауты на шлюзе.

---

## 2. Изменения в инфраструктуре

### 2.1 Изоляция данных (PostgreSQL)
Для микросервиса выделяется изолированная база данных (или схема) `geoinfo_print`.
```sql
CREATE TABLE print_tasks (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    layout VARCHAR(64) NOT NULL,
    s3_url VARCHAR(512),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_print_tasks_status ON print_tasks(status);

```

### 2.2 Конфигурация Docker Compose

Добавление сервиса в существующий `docker-compose.yml`:

```yaml
  geo-print-service:
    build:
      context: ./geo-print-service
      dockerfile: Dockerfile
    container_name: geo-print-service
    environment:
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/geoinfo_print
      - AWS_S3_ENDPOINT=http://minio:9000
      - AWS_S3_BUCKET=print-reports
      - GEOSERVER_INTERNAL_URL=http://geoserver:8080/geoserver
    networks:
      - geoinfo-network
    depends_on:
      - kafka
      - postgres
      - minio

```

*Примечание: S3 Багет `print-reports` должен быть создан автоматически при инициализации приложения или иметь политику жизненного цикла (Lifecycle Policy) автоматического удаления файлов через 7 дней для экономии места.*

---

## 3. Спецификация контрактов (API & Messaging)

### 3.1 HTTP API Эндпоинты

#### **Создание задачи на печать**

* **URL:** `POST /api/v1/print/tasks`
* **Content-Type:** `application/json`
* **Тело запроса (`PrintSpecification JSON`):**

```json
{
  "layout": "A4_LANDSCAPE",
  "dpi": 300,
  "mapContext": {
    "projection": "EPSG:3857",
    "bbox": [8300000.0, 5200000.0, 8350000.0, 5250000.0],
    "rotation": 0
  },
  "layers": [
    {
      "type": "WMS",
      "url": "http://geoserver:8080/geoserver/wms",
      "layerName": "workspace:satellite_ortho",
      "opacity": 1.0
    },
    {
      "type": "VECTOR",
      "features": {
        "type": "FeatureCollection",
        "features": [
          {
            "type": "Feature",
            "geometry": { "type": "Polygon", "coordinates": [[[8305000, 5205000], [8310000, 5205000], [8310000, 5210000], [8305000, 5210000], [8305000, 5205000]]] },
            "properties": { "id": "cadastre_102" }
          }
        ]
      },
      "style": {
        "strokeColor": "#FF0000",
        "strokeWidth": 2,
        "fillColor": "rgba(255, 0, 0, 0.2)"
      }
    }
  ],
  "attributes": {
    "title": "План земельного участка №102",
    "author": "Нурболат",
    "organization": "ГеоИнфоСистемы ОО"
  }
}

```

* **Ответ сервера (`HTTP 202 Accepted`):**

```json
{
  "taskId": "a3b9c8d7-e6f5-4a3b-2c1d-0e9f8a7b6c5d",
  "status": "PENDING",
  "estimatedTimeSeconds": 15
}

```

---

### 3.2 События Kafka

* **Топик:** `geo.print.tasks`
* **Структура сообщения (Key: `taskId`, Value: `JSON`):**

```json
{
  "taskId": "a3b9c8d7-e6f5-4a3b-2c1d-0e9f8a7b6c5d",
  "timestamp": 1779782400000
}

```

---

## 4. Реализация Бэкенда (`geo-print-service`)

### 4.1 Зависимости (`pom.xml`)

Для работы с геоданными и компиляции PDF в проект добавляются следующие ключевые репозитории и библиотеки:

```xml
<repositories>
    <repository>
        <id>osgeo</id>
        <name>OSGeo Release Repository</name>
        <url>[https://repo.osgeo.org/repository/release/](https://repo.osgeo.org/repository/release/)</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.geotools</groupId>
        <artifactId>gt-render</artifactId>
        <version>${geotools.version}</version>
    </dependency>
    <dependency>
        <groupId>org.geotools</groupId>
        <artifactId>gt-wms</artifactId>
        <version>${geotools.version}</version>
    </dependency>
    <dependency>
        <groupId>org.geotools</groupId>
        <artifactId>gt-geojson</artifactId>
        <version>${geotools.version}</version>
    </dependency>

    <dependency>
        <groupId>com.openhtmltopdf</groupId>
        <artifactId>openhtmltopdf-core</artifactId>
        <version>${openhtml.version}</version>
    </dependency>
    <dependency>
        <groupId>com.openhtmltopdf</groupId>
        <artifactId>openhtmltopdf-pdfbox</artifactId>
        <version>${openhtml.version}</version>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
</dependencies>

```

### 4.2 Ключевой компонент: Логика рендеринга (`MapRenderer.java`)

Парсинг спецификации контекста карты и отрисовка ее в `BufferedImage`:

```java
public BufferedImage renderMap(PrintSpecification spec) throws Exception {
    int width = calculateWidthInPixels(spec.getLayout(), spec.getDpi());
    int height = calculateHeightInPixels(spec.getLayout(), spec.getDpi());
    
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();
    
    // Настройка сглаживания
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    
    MapContent mapContent = new MapContent();
    
    // 1. Сборка WMS слоев
    for (LayerSpec layer : spec.getLayers()) {
        if ("WMS".equals(layer.getType())) {
            WMSMapLayer wmsLayer = createWmsLayer(layer, spec.getMapContext().getBbox());
            mapContent.addLayer(wmsLayer);
        } else if ("VECTOR".equals(layer.getType())) {
            FeatureLayer vectorLayer = createVectorLayer(layer);
            mapContent.addLayer(vectorLayer);
        }
    }
    
    // 2. Определение границ отображения (Viewport)
    GTRenderer renderer = new StreamingRenderer();
    renderer.setMapContent(mapContent);
    
    ReferencedEnvelope mapArea = new ReferencedEnvelope(
        spec.getMapContext().getBbox()[0], spec.getMapContext().getBbox()[2],
        spec.getMapContext().getBbox()[1], spec.getMapContext().getBbox()[3],
        CRS.decode(spec.getMapContext().getProjection())
    );
    
    Rectangle paintArea = new Rectangle(0, 0, width, height);
    
    // Отрисовка ГИС слоев в контекст графики Java
    renderer.paint(g2d, paintArea, mapArea);
    g2d.dispose();
    mapContent.dispose();
    
    return image;
}

```

### 4.3 Сборка итогового документа (`PdfBuilder.java`)

Использование Thymeleaf для наполнения шаблона и конвертация структуры в PDF:

```java
public byte[] buildPdfReport(BufferedImage mapImage, PrintSpecification spec) throws Exception {
    // Конвертация BufferedImage карты в Base64 для инлайна в HTML шаблон
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(mapImage, "png", baos);
    String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
    
    Context context = new Context();
    context.setVariable("mapImageBase64", "data:image/png;base64," + base64Image);
    context.setVariable("attr", spec.getAttributes());
    context.setVariable("layout", spec.getLayout());
    
    // Генерация HTML строки на основе шаблона (layouts/print_layout.html)
    String htmlContent = templateEngine.process("layouts/print_layout", context);
    
    ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
    PdfRendererBuilder builder = new PdfRendererBuilder();
    builder.useFastMode();
    builder.withHtmlContent(htmlContent, "/");
    builder.toStream(pdfOutputStream);
    builder.run();
    
    return pdfOutputStream.toByteArray();
}

```

---

## 5. Шаблон разметки документа (`print_layout.html`)

Разметка страниц с использованием CSS Paged Media для корректного разделения границ листа при печати:

```html
<!DOCTYPE html>
<html xmlns:th="[http://www.thymeleaf.org](http://www.thymeleaf.org)">
<head>
    <style>
        @page {
            size: A4 landscape;
            margin: 10mm;
        }
        body {
            font-family: 'DejaVu Sans', sans-serif; /* Поддержка кириллицы */
            margin: 0;
            padding: 0;
        }
        .container {
            width: 100%;
            height: 100%;
        }
        .map-frame {
            width: 100%;
            height: 75%;
            border: 1px solid #000;
            text-align: center;
        }
        .map-img {
            width: 100%;
            height: auto;
        }
        .stamp-table {
            width: 100%;
            margin-top: 10px;
            border-collapse: collapse;
        }
        .stamp-table td {
            border: 1px solid #000;
            padding: 5px;
            font-size: 10px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h2 th:text="${attr.title}">Название чертежа</h2>
        <div class="map-frame">
            <img class="map-img" th:src="${mapImageBase64}" />
        </div>
        
        <table class="stamp-table">
            <tr>
                <td style="width: 20%;">Разработал:</td>
                <td style="width: 30%;" th:text="${attr.author}">Нурболат</td>
                <td style="width: 20%;">Организация:</td>
                <td style="width: 30%;" th:text="${attr.organization}">ГеоИнфоСистемы</td>
            </tr>
            <tr>
                <td>Дата создания:</td>
                <td th:text="${#dates.format(#dates.createNow(), 'dd.MM.yyyy HH:mm')}">25.05.2026</td>
                <td>Масштаб:</td>
                <td>Указан на карте</td>
            </tr>
        </table>
    </div>
</body>
</html>

```

---

## 6. Логика Фронтенда (Vue.js 3 + OpenLayers)

Фронтенд вычисляет точный географический экстент (`BBox`), просматриваемый пользователем в рамке макета печати, чтобы передать его на бэкенд:

```typescript
import { Map } from 'ol';
import { getWidth, getHeight } from 'ol/extent';

function preparePrintSpecification(map: Map, selectedLayout: string): any {
  const view = map.getView();
  const extent = view.calculateExtent(map.getSize()); // Либо экстент рамки-выбора
  
  // Получаем список активных слоев
  const printLayers = map.getLayers().getArray()
    .filter(layer => layer.getVisible())
    .map(layer => {
      const source = layer.getSource();
      if (source instanceof TileWMS) {
        return {
          type: 'WMS',
          url: source.getUrl(),
          layerName: source.getParams().LAYERS,
          opacity: layer.getOpacity()
        };
      }
      // Обработка векторных слоев на клиенте
      if (source instanceof VectorSource) {
        const geojsonFormat = new GeoJSON();
        const features = source.getFeatures();
        return {
          type: 'VECTOR',
          features: geojsonFormat.writeFeaturesObject(features),
          style: { strokeColor: '#FF0000', strokeWidth: 2 }
        };
      }
    });

  return {
    layout: selectedLayout,
    dpi: 300,
    mapContext: {
      projection: view.getProjection().getCode(),
      bbox: extent,
      rotation: view.getRotation()
    },
    layers: printLayers.filter(Boolean),
    attributes: {
      title: "Экспорт карты",
      author: "Пользователь"
    }
  };
}

```

---

## 7. Пошаговый план разработки (Solo-Developer MVP Tracker)

* [ ] **Шаг 1: Скелет сервиса.** Создать модуль `geo-print-service`, подключить к общей шине Kafka, настроить схему в БД и сконфигурировать S3-клиент для MinIO.
* [ ] **Шаг 2: Базовый HTML рендеринг.** Реализовать сборку простейшего текстового PDF-документа через `OpenHTMLtoPDF` без карты, для проверки работы пайплайна загрузки в MinIO.
* [ ] **Шаг 3: Интеграция GeoTools.** Написать движок `MapRenderer` для загрузки одной базовой WMS-подложки из GeoServer по заданному BBox и запекания ее в картинку.
* [ ] **Шаг 4: Слияние.** Соединить картинку из Шага 3 с шаблоном из Шага 2. Добиться выгрузки полноценного листа А4 с картой.
* [ ] **Шаг 5: Векторы и Контракт.** Добавить парсинг GeoJSON объектов для отрисовки пользовательских полигонов поверх растровой карты. Подключить Vue.js UI интерфейс.

```

***

Документ полностью готов для фиксации архитектурных решений по первой фазе. Можно переносить в проект и переходить непосредственно к написанию кода `geo-print-service`!

```