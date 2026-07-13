## **Финальная Схема БД (GeoAbstraction Service)**

База данных для управления задачами обработки растров, рельефа и ГИС-анализа.

### **1. Таблица geo_abstract_jobs (Задачи обработки)**

Хранит информацию о задачах верификации и асинхронной обработки растров и рельефа.

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `project_id` | UUID | ID проекта, в контексте которого запущена задача (nullable) |
| `name` | VARCHAR(255) | Имя задачи |
| `status` | VARCHAR(50) | Статус (VERIFYING, VERIFIED, QUEUED, PROCESSING, READY, FAILED) |
| `task_type` | VARCHAR(50) | Тип задачи (VERIFY_FILE, TERRAIN_MESH, SENTINEL_COG, LANDSAT_COG, NETCDF_COG и др.) |
| `source_bucket` | VARCHAR(255) | Бакет MinIO с исходным файлом |
| `source_object_key` | VARCHAR(255) | Ключ исходного файла |
| `output_bucket` | VARCHAR(255) | Бакет MinIO с выходными файлами |
| `output_prefix` | VARCHAR(255) | Префикс/путь для выходных файлов |
| `crs` | VARCHAR(50) | CRS исходного файла |
| `bbox` | GEOMETRY(MultiPolygon, 4326) | Пространственные границы данных (экстент) |
| `min_height` | DOUBLE PRECISION | Минимальная высота (для DEM/рельефа) |
| `max_height` | DOUBLE PRECISION | Максимальная высота (для DEM/рельефа) |
| `characteristics` | JSONB | Выявленные метаданные и параметры обработки (каналы, индексы) |
| `error_message` | TEXT | Сообщение об ошибке при сбое задачи |
| `file_size` | BIGINT | Размер исходного файла в байтах |

### **2. Таблица analysis.analysis_tasks (Задачи ГИС-анализа)**

Хранит информацию о задачах пространственного анализа (буферы, NDVI, водосборы, изогипсы и др.).

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `plugin_name` | VARCHAR(100) | Название аналитического плагина |
| `status` | VARCHAR(20) | Статус выполнения (QUEUED, PROCESSING, READY, FAILED) |
| `input_params` | JSONB | Входные параметры ГИС-плагина |
| `s3_input_paths` | JSONB | Пути к входным векторным/растровым файлам в MinIO |
| `s3_output_paths` | JSONB | Пути к сгенерированным результатам анализа в MinIO |
| `error_message` | TEXT | Текст ошибки в случае сбоя аналитики |
| `user_id` | UUID | Идентификатор пользователя |
| `project_id` | UUID | ID проекта |

### **3. Таблица analysis.plugin_schemas (Схемы плагинов)**

Хранит динамически регистрируемые JSON-схемы ГИС-плагинов для рендеринга интерфейса (Schema-Driven UI).

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| `plugin_name` | VARCHAR(100) | Первичный ключ. Системное имя плагина. |
| `title` | VARCHAR(255) | Человекочитаемое название плагина |
| `icon` | VARCHAR(100) | Иконка (MDI) для интерфейса |
| `schema` | JSONB | JSON-схема входных параметров (Draft-07) |
| `registered_at` | TIMESTAMP | Дата и время регистрации плагина воркером |
