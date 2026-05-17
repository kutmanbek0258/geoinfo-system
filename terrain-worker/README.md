# Terrain Worker

Специализированный воркер на Python для генерации 3D-рельефа (Cesium Quantized-Mesh).

## Основные возможности

- **Terrain Processing (`TERRAIN_MESH`):** Конвертация DEM GeoTIFF в формат **Cesium Quantized-Mesh**.
- Использует **Cesium Terrain Builder (CTB)** для эффективной нарезки тайлов.

## Технологический стек

- **Python 3.7+**
- **GDAL** (перепроецирование и подготовка DEM)
- **Cesium Terrain Builder (CTB)** — основная утилита для генерации сетки.
- **Kafka-python** — для асинхронного взаимодействия.
- **MinIO Python SDK** — для работы с объектным хранилищем.

## Настройка (Environment Variables)

| Переменная | Описание | Значение по умолчанию |
| :--- | :--- | :--- |
| `KAFKA_BOOTSTRAP_SERVERS` | Адрес брокера Kafka | `kafka:9092` |
| `KAFKA_TOPIC` | Топик для событий | `geoabstraction.terrain.events` |
| `KAFKA_GROUP_ID` | Группа потребителей Kafka | `terrain-worker-group` |
| `TERRAIN_STORE` | Путь к хранилищу 3D тайлов | `/data/terrain-store` |

## Взаимодействие

Воркер слушает топик `geoabstraction.data.events` и обрабатывает задачи типа `TERRAIN_MESH`. Результаты сохраняются в `/data/terrain-store`, который обычно монтируется как общий том с Nginx для раздачи тайлов.
