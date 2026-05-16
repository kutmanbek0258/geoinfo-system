# GeoAbstraction Service

Микросервис для управления задачами сложной обработки геоданных (рельеф, спутниковые снимки и др.). Входит в состав системы **ГеоИнфоСистема**.

## Основные функции

- **Загрузка данных:** Прием исходных GeoTIFF и SAFE (Sentinel-2) файлов через API.
- **Управление задачами (Jobs):** Поддержка различных типов обработки данных:
  - `TERRAIN_MESH`: Генерация 3D-рельефа (Cesium Quantized-Mesh).
  - `SENTINEL_COG`: Преобразование пакетов Sentinel-2 в Cloud Optimized GeoTIFF (COG).
- **Управление слоями:** Хранение метаданных готовых слоев (для рельефа).
- **Очистка данных:** Удаление сгенерированных файлов при удалении слоев или задач.

## Технологический стек

- **Java 17** и **Spring Boot 3**
- **Spring Data JPA** + **PostGIS** (хранение метаданных в схеме `geoabstraction`)
- **Spring Kafka** (взаимодействие с GeoAbstraction Worker)
- **MinIO SDK** (хранение исходных файлов)
- **Liquibase** (миграции базы данных)

## Взаимодействие в системе

1. Пользователь загружает данные (GeoTIFF или Sentinel-2 ZIP) через `GeoAbstraction Service`.
2. Сервис сохраняет файл в **MinIO** (bucket: `geo-abstraction-input`) и создает задачу в БД (`geo_abstract_jobs`).
3. Отправляется событие в **Kafka** (топик `geoabstraction.data.events`).
4. **GeoAbstraction Worker** (Python) подхватывает задачу, определяет тип (`task_type`) и выполняет соответствующую обработку.
5. `GeoAbstraction Service` получает уведомление об успехе через Kafka, обновляет статус и (для рельефа) создает запись в слоях.

## API Endpoints

### Задачи (Jobs)
- `POST /api/geo-abstraction/jobs` — Создать задачу `TERRAIN_MESH` (рельеф).
- `POST /api/geo-abstraction/sentinel/upload` — Создать задачу `SENTINEL_COG` (Sentinel-2). Параметры: `name`, `file`, `channels` (список каналов).
- `GET /api/geo-abstraction/jobs/{id}` — Получить статус и детали конкретной задачи.

### Слои (Layers)
- `GET /api/geo-abstraction/layers` — Список всех доступных слоев (с пагинацией).
- `DELETE /api/geo-abstraction/layers/{id}` — Удалить слой и связанные файлы.

## Настройка (Environment Variables)

| Переменная | Описание | Значение по умолчанию |
| :--- | :--- | :--- |
| `DB_URL` | URL к PostgreSQL/PostGIS | `jdbc:postgresql://postgres-geoabstraction:5432/geoabstraction_db` |
| `DB_USER` | Пользователь БД | `postgres` |
| `DB_PASS` | Пароль БД | `password` |
| `KAFKA_BOOTSTRAP_SERVERS` | Адрес брокера Kafka | `kafka:9092` |
| `MINIO_ROOT_USER` | Access Key для MinIO | `minio_access_key` |
| `MINIO_ROOT_PASSWORD` | Secret Key для MinIO | `minio_secret_key` |

## База данных

Сервис использует схему `geoabstraction` в базе данных PostgreSQL. Основные таблицы:
- `geo_abstract_jobs`: Журнал всех задач обработки.
- `terrain_layers`: Реестр готовых 3D слоев.
