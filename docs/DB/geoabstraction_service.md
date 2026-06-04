## **Финальная Схема БД (GeoAbstraction Service)**

База данных для управления задачами обработки растров, рельефа и опубликованными слоями.

### **1. Таблица imagery_layers (Слои снимков)**

Хранит метаданные опубликованных растровых слоев в GeoServer.

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| id | UUID | Primary Key |
| job_id | UUID | ID задачи, создавшей слой (Foreign Key) |
| name | VARCHAR(255) | Название слоя |
| description | TEXT | Описание |
| workspace | VARCHAR(128) | Workspace в GeoServer |
| layer_name | VARCHAR(256) | Имя слоя в GeoServer |
| service_url | VARCHAR(255) | Базовый URL WMS сервиса |
| status | VARCHAR(50) | Статус (ACTIVE, DELETING и др.) |
| style | VARCHAR(128) | Примененный стиль SLD |
| date_captured | TIMESTAMP | Дата съемки |
| crs | VARCHAR(32) | Система координат |
| characteristics | JSONB | Доп. метаданные (индекс, каналы и др.) |
| cog_object_key | VARCHAR(512) | Ключ COG-файла в MinIO |
| bbox | GEOMETRY(MultiPolygon, 4326) | Пространственные границы слоя |

### **2. Таблица geo_abstract_jobs (Задачи обработки)**

Хранит информацию о задачах обработки растров и рельефа.

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| id | UUID | Primary Key |
| name | VARCHAR(255) | Имя задачи |
| status | VARCHAR(50) | Статус (QUEUED, PROCESSING, READY, FAILED) |
| task_type | VARCHAR(50) | Тип задачи (TERRAIN_MESH, SENTINEL_COG и др.) |
| source_bucket | VARCHAR(255) | Бакет MinIO с исходным файлом |
| source_object_key | VARCHAR(255) | Ключ исходного файла |
| output_prefix | VARCHAR(255) | Префикс для выходных файлов |
| crs | VARCHAR(50) | CRS исходного файла |
| bbox | GEOMETRY(MultiPolygon, 4326) | Экстент данных |
| min_height | DOUBLE | Минимальная высота (для рельефа) |
| max_height | DOUBLE | Максимальная высота (для рельефа) |
| characteristics | JSONB | Параметры обработки (каналы, индексы) |
| error_message | TEXT | Текст ошибки при сбое |
| file_size | BIGINT | Размер исходного файла |

### **3. Таблица terrain_layers (3D Слои рельефа)**

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| id | UUID | Primary Key |
| job_id | UUID | Связь с задачей |
| title | VARCHAR(255) | Заголовок слоя |
| terrain_url | VARCHAR(512) | URL для Cesium TerrainProvider |
| status | VARCHAR(50) | Статус готовности |
| is_visible | BOOLEAN | Флаг видимости по умолчанию |
