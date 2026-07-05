## **Финальная Схема БД (GeoAbstraction Service)**

База данных для управления задачами обработки растров, рельефа и опубликованными слоями.

### **1. Таблица imagery_layers (Слои снимков)**

Хранит метаданные растровых слоев, обслуживаемых через TiTiler.

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| id | UUID | Primary Key |
| job_id | UUID | ID задачи, создавшей слой (Foreign Key) |
| name | VARCHAR(255) | Название слоя |
| description | TEXT | Описание |
| workspace | VARCHAR(128) | (Устарело) Workspace (сохранено для совместимости) |
| layer_name | VARCHAR(256) | Имя слоя в хранилище / префикс |
| service_url | VARCHAR(255) | (Устарело) Базовый URL сервиса |
| status | VARCHAR(50) | Статус (ACTIVE, DELETING и др.) |
| style | VARCHAR(128) | Примененный стиль отображения |
| date_captured | TIMESTAMP | Дата съемки |
| crs | VARCHAR(32) | Система координат |
| characteristics | JSONB | Доп. метаданные (индекс, каналы и др.) |
| project_id | UUID | ID проекта (nullable). Если NULL — слой общий. |
| cog_object_key | VARCHAR(512) | Ключ COG-файла в MinIO |
| bbox | GEOMETRY(MultiPolygon, 4326) | Пространственные границы слоя |

### **2. Таблица geo_abstract_jobs (Задачи обработки)**

Хранит информацию о задачах верификации и обработки растров и рельефа.

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| id | UUID | Primary Key |
| project_id | UUID | ID проекта, в контексте которого запущена задача (nullable) |
| name | VARCHAR(255) | Имя задачи |
| status | VARCHAR(50) | Статус (VERIFYING, VERIFIED, QUEUED, PROCESSING, READY, FAILED) |
| task_type | VARCHAR(50) | Тип задачи (VERIFY_FILE, TERRAIN_MESH, SENTINEL_COG, LANDSAT_COG, NETCDF_COG и др.) |
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
| project_id | UUID | ID проекта (nullable). Если NULL — слой общий. |
| title | VARCHAR(255) | Заголовок слоя |
| terrain_url | VARCHAR(512) | URL для Cesium TerrainProvider |
| status | VARCHAR(50) | Статус готовности |
| is_visible | BOOLEAN | Флаг видимости по умолчанию |
