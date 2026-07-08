# GeoAbstraction Service

Микросервис для управления задачами сложной обработки геоданных (рельеф, спутниковые снимки, NetCDF и др.). Входит в состав системы **ГеоИнфоСистема**.

## Основные функции

- **Двухэтапный импорт файлов:**
  - **Шаг 1: Загрузка и верификация:** Получение Presigned URL ➔ прямая загрузка файла в S3 (MinIO) минуя бэкенд ➔ автоматический запуск верификационной задачи с извлечением метаданных (CRS, BBox, спектральные каналы, NetCDF-переменные).
  - **Шаг 2: Параметризованный импорт:** Запуск финальной обработки и нарезки данных на основе параметров, выбранных пользователем (RGB-пресеты, спектральные индексы, субдатасеты NetCDF).
- **Поддерживаемые типы данных:**
  - `SENTINEL_COG`: Преобразование пакетов Sentinel-2 в Cloud Optimized GeoTIFF (COG).
  - `LANDSAT_COG`: Преобразование архивов Landsat-8 в COG.
  - `RAW_GEOTIFF_OPTIMIZE`: Оптимизация сырых GeoTIFF файлов в COG.
  - `NETCDF_COG`: Извлечение выбранной переменной NetCDF и конвертация в COG.
  - `TERRAIN_MESH`: Нарезка DEM-высот в quantized-mesh слои 3D-рельефа для Cesium.
- **Интеграция с TiTiler:** Все сгенерированные COG-файлы публикуются в таблице `imagery_layers` и обслуживаются тайл-сервером TiTiler напрямую из S3 по протоколу XYZ.

## Технологический стек

- **Java 17** и **Spring Boot 3**
- **Spring Data JPA** + **PostGIS** (хранение метаданных в схеме `geoabstraction`)
- **Spring Kafka** (оркестрация задач между бэкендом и воркерами)
- **MinIO SDK** (управление исходными и выходными файлами)
- **Liquibase** (миграции структуры БД и системных стилей)

## Взаимодействие в системе

1. Фронтенд запрашивает ссылку через `/api/geo-abstraction/upload/presigned-url` и загружает файл методом `PUT` напрямую в **MinIO** (bucket: `geo-abstraction-input`).
2. Фронтенд инициирует верификацию через `/api/geo-abstraction/jobs/verify-upload`. Создается задача `VERIFY_FILE` в статусе `VERIFYING`, событие отправляется в топик `geoabstraction.raster.events` в **Kafka**.
3. **GeoAbstraction Worker** (Python) скачивает файл, анализирует его с помощью **GDAL/OGR** и возвращает метаданные в ответе `READY`. Бэкенд сохраняет их в JSONB `characteristics` задачи и переводит статус в `VERIFIED`.
4. Пользователь выбирает параметры и запускает импорт `/api/geo-abstraction/jobs/{id}/import`. Задача переходит в статус `QUEUED`, событие летит в Kafka.
5. Соответствующий воркер обрабатывает данные, генерирует выходные слои (COG или quantized-mesh) и отправляет статус `READY`.
6. Бэкенд регистрирует слои в реестре `imagery_layers` (растры для TiTiler) или `terrain_layers` (рельеф для Cesium).

## API Endpoints

### Загрузка и Верификация
- `GET /api/geo-abstraction/upload/presigned-url?filename=...` — Получить временную ссылку для прямой загрузки файла в S3.
- `POST /api/geo-abstraction/jobs/verify-upload` — Зарегистрировать загруженный файл и запустить верификацию (`VERIFY_FILE`).
- `POST /api/geo-abstraction/jobs/{id}/import` — Запустить финальную обработку верифицированной задачи с параметрами.

### Задачи (Jobs)
- `GET /api/geo-abstraction/jobs/{id}` — Статус и детальные характеристики задачи.
- `GET /api/geo-abstraction/jobs` — Список задач проекта с пагинацией.

### Слои (Layers)
- `GET /api/geo-abstraction/layers` — Список всех доступных растровых слоев.
- `DELETE /api/geo-abstraction/layers/{id}` — Удалить растровый слой и его файлы в S3.

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

Сервис использует схему `geoabstraction` в PostgreSQL. Основные таблицы:
- `geo_abstract_jobs`: Журнал задач (верификации и импорта).
- `raster_styles`: Системные и пользовательские палитры цветов/стилей для растров.
- `imagery_layers`: Реестр готовых 2D-слоев (COG).
- `terrain_layers`: Реестр готовых 3D-слоев рельефа.
