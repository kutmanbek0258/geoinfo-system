# GeoAbstract Worker

Специализированный воркер на Python для общей обработки геопространственных данных на базе GDAL.

## Основные возможности

- **Sentinel-2 Processing (`SENTINEL_COG`):** 
  - Извлечение спектральных каналов из `.SAFE` (ZIP) архивов.
  - Слияние выбранных каналов в многоканальный файл.
  - Конвертация в **Cloud Optimized GeoTIFF (COG)** для высокопроизводительного обслуживания тайлов.

## Технологический стек

- **Python 3.10+**
- **GDAL 3.6+** (основная библиотека для обработки растров)
- **Kafka-python** — для асинхронного взаимодействия.
- **MinIO Python SDK** — для работы с объектным хранилищем.

## Настройка (Environment Variables)

| Переменная | Описание | Значение по умолчанию |
| :--- | :--- | :--- |
| `KAFKA_BOOTSTRAP_SERVERS` | Адрес брокера Kafka | `kafka:9092` |
| `KAFKA_TOPIC` | Топик для событий | `geoabstraction.raster.events` |
| `KAFKA_GROUP_ID` | Группа потребителей Kafka | `geoabstract-worker-group` |
| `GDAL_STORE` | Путь к хранилищу COG файлов | `/data/gdal-store` |

## Взаимодействие с GeoServer

При выполнении задач `SENTINEL_COG` итоговые файлы сохраняются в `/data/gdal-store`. Этот путь смонтирован как общий том с контейнером GeoServer, что позволяет выполнять публикацию слоев через GeoServer REST API или интерфейс.
