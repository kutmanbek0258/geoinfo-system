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

### **2. Таблица terrain_jobs (Задачи по рельефу)**

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| id | UUID | Primary Key |
| name | VARCHAR(255) | Имя задачи |
| status | VARCHAR(50) | Статус (QUEUED, PROCESSING, READY, FAILED) |
| source_bucket | VARCHAR(255) | Бакет MinIO с исходным DEM |
| source_object_key | VARCHAR(255) | Ключ исходного файла |
| output_prefix | VARCHAR(255) | Префикс для выходных файлов |
| crs | VARCHAR(50) | CRS исходного файла |
| bbox | GEOMETRY | Экстент данных |
| min_height | DOUBLE | Минимальная высота |
| max_height | DOUBLE | Максимальная высота |
| error_message | TEXT | Текст ошибки при сбое |

### **3. Таблица terrain_layers (3D Слои рельефа)**

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| id | UUID | Primary Key |
| job_id | UUID | Связь с задачей |
| title | VARCHAR(255) | Заголовок слоя |
| terrain_url | VARCHAR(512) | URL для Cesium TerrainProvider |
| status | VARCHAR(50) | Статус готовности |
| is_visible | BOOLEAN | Флаг видимости по умолчанию |
