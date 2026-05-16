# GeoAbstraction Worker

Специализированный воркер на Python для сложной обработки геопространственных данных (GDAL/CTB).

## Основные возможности

- **Terrain Processing (`TERRAIN_MESH`):** Конвертация DEM GeoTIFF в формат **Cesium Quantized-Mesh**.
- **Sentinel-2 Processing (`SENTINEL_COG`):** 
  - Извлечение спектральных каналов из `.SAFE` (ZIP) архивов.
  - Слияние выбранных каналов в многоканальный файл.
  - Конвертация в **Cloud Optimized GeoTIFF (COG)** для высокопроизводительного обслуживания тайлов.

## Технологический стек

- **Python 3.9+**
- **GDAL** (библиотека геоданных для COG и базовых операций)
- **Cesium Terrain Builder (CTB)** — для генерации сетки рельефа.
- **Kafka-python** — для асинхронного взаимодействия.
- **MinIO Python SDK** — для работы с объектным хранилищем.

## Настройка (Environment Variables)

| Переменная | Описание | Значение по умолчанию |
| :--- | :--- | :--- |
| `KAFKA_BOOTSTRAP_SERVERS` | Адрес брокера Kafka | `kafka:9092` |
| `KAFKA_TOPIC` | Топик для событий | `geoabstraction.data.events` |
| `TERRAIN_STORE` | Путь к хранилищу 3D тайлов | `/data/terrain-store` |
| `GDAL_STORE` | Путь к хранилищу COG файлов | `/data/gdal-store` |

## Взаимодействие с GeoServer

При выполнении задач `SENTINEL_COG` итоговые файлы сохраняются в `/data/gdal-store`. Этот путь смонтирован как общий том с контейнером GeoServer, что позволяет администратору выполнять ручную публикацию (Вариант А) через интерфейс GeoServer, выбирая файлы из внутренней директории `/data/gdal-store`.

## Пример события Sentinel-2

```json
{
  "jobId": "uuid",
  "taskType": "SENTINEL_COG",
  "eventType": "QUEUED",
  "sourceBucket": "geo-abstraction-input",
  "sourceObjectKey": "uploads/sentinel-pack.zip",
  "characteristics": {
    "channels": ["B04", "B03", "B02"]
  }
}
```
