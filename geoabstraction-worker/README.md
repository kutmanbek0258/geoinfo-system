# Terrain Worker

Специализированный воркер на Python для конвертации растровых данных рельефа (GeoTIFF) в формат **Cesium Quantized-Mesh**.

## Принцип работы

Воркер работает в фоновом режиме, слушая задачи из Kafka. Процесс обработки включает:
1. **Загрузка:** Получение исходного TIFF файла из MinIO.
2. **Конвертация (GDAL):** Перепроецирование в EPSG:4326 и нормализация данных.
3. **Генерация тайлов (CTB):** Разбиение рельефа на иерархическую структуру тайлов `.terrain`.
4. **Метаданные:** Генерация и коррекция файла `layer.json` для совместимости с CesiumJS.
5. **Публикация:** Перемещение готовых тайлов в общее хранилище (Shared Volume), доступное Nginx.

- **Очистка:** Удаление директории с тайлами из хранилища при получении события `DELETED`.

## Технологический стек

- **Python 3.7+**
- **GDAL** (библиотека геоданных)
- **Cesium Terrain Builder (CTB)** — утилита `ctb-tile` для генерации quantized-mesh.
- **Kafka-python** — для получения задач и отправки статусов.
- **MinIO Python SDK** — для работы с хранилищем файлов.

## Настройка (Environment Variables)

| Переменная | Описание | Значение по умолчанию |
| :--- | :--- | :--- |
| `KAFKA_BOOTSTRAP_SERVERS` | Адрес брокера Kafka | `kafka:9092` |
| `KAFKA_TOPIC` | Топик для задач и статусов | `geoabstraction.data.events` |
| `MINIO_ENDPOINT` | Эндпоинт MinIO | `minio:9000` |
| `MINIO_ACCESS_KEY` | Access Key для MinIO | `minio_access_key` |
| `MINIO_SECRET_KEY` | Secret Key для MinIO | `minio_secret_key` |
| `TERRAIN_STORE` | Путь к папке для сохранения тайлов | `/data/terrain-store` |
| `CTB_ZOOM` | Диапазон уровней зума для генерации | `0-22` |

## Взаимодействие с файловой системой

Воркер ожидает, что директория, указанная в `TERRAIN_STORE`, является смонтированным Volume, который также подключен к **nginx-proxy**. Это позволяет CesiumJS запрашивать тайлы рельефа напрямую через веб-сервер по URL, который формирует `Terrain Service`.

## Пример события в Kafka (QUEUED)

```json
{
  "jobId": "uuid",
  "eventType": "QUEUED",
  "sourceBucket": "terrain",
  "sourceObjectKey": "uploads/input.tif",
  "outputPrefix": "my-layer-abc123"
}
```
