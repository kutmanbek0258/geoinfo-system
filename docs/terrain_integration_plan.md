# План интеграции 3D Terrain на базе `tum-gis/cesium-terrain-builder-docker` в GeoInfo System

## 1. Цель

Добавить в GeoInfo System новый поток обработки данных, который позволяет:

1. Пользователю загрузить GeoTIFF.
2. Сохранить оригинальный файл в MinIO.
3. Асинхронно сгенерировать Cesium Terrain (`quantized-mesh`) через Docker-образ `tum-gis/cesium-terrain-builder-docker`.
4. Отдать готовый terrain по HTTP.
5. Открыть его в фронтенде через CesiumJS.

Результат должен быть полностью self-hosted и не зависеть от Cesium ion.

---

## 2. Что уже есть в системе и как это использовать

В текущем GeoInfo System уже есть подходящая основа для расширения:

- микросервисная архитектура;
- API Gateway;
- отдельный `geodata-service` для работы с геоданными;
- MinIO для файлов;
- PostgreSQL/PostGIS для пространственных данных;
- Kafka для асинхронных событий;
- frontend на Vue.js.

Это означает, что terrain-пайплайн не нужно внедрять в документы, GeoServer или векторную логику. Его лучше выделить как отдельный контур обработки.

---

## 3. Рекомендуемая архитектура

### 3.1. Новый контур для terrain

Добавить два новых компонента:

- `terrain-service` — Spring Boot сервис для загрузки GeoTIFF, хранения метаданных, управления статусами и выдачи URL.
- `terrain-worker` — отдельный контейнер на базе `tum-gis/cesium-terrain-builder-docker`, который делает генерацию terrain.

### 3.2. Данные и хранилища

- **MinIO** — хранение исходного GeoTIFF и готовых terrain-артефактов.
- **PostgreSQL / PostGIS** — хранение метаданных, статусов, привязки к проектам и геометрии экстентов.
- **Kafka** — асинхронные события между сервисом и worker.
- **Nginx** или MinIO public bucket — HTTP-раздача готового terrain.

### 3.3. Поток данных

```text
User uploads GeoTIFF
        ↓
API Gateway
        ↓
terrain-service
        ↓
MinIO (source file)
        ↓
Kafka job event
        ↓
terrain-worker
        ↓
ctb-tile / quantized-mesh generation
        ↓
MinIO or nginx public folder
        ↓
terrain-service marks READY
        ↓
CesiumJS opens terrain
```

---

## 4. Почему нужен отдельный `terrain-service`

Не рекомендуется класть terrain-обработку в `geodata-service`, потому что:

1. Генерация terrain — тяжёлая и долгоживущая операция.
2. Она требует отдельного жизненного цикла job.
3. Нужен отдельный статусный pipeline: `UPLOADED → QUEUED → PROCESSING → READY / FAILED`.
4. Легче масштабировать worker отдельно.
5. В будущем можно добавить другие генераторы: DSM, DTM, point cloud, 3D mesh.

`geodata-service` при этом можно оставить ответственным за каталог проектов и привязку terrain-слоёв к проекту.

---

## 5. План изменений в backend

### 5.1. Новый микросервис `terrain-service`

Задачи сервиса:

- принимать upload GeoTIFF;
- сохранять оригинал в MinIO;
- создавать запись terrain job;
- публиковать событие в Kafka;
- отдавать статус job;
- хранить ссылку на готовый terrain;
- возвращать Cesium-ready URL.

### 5.2. Основные сущности

#### `TerrainJob`

Поля:

- `id: UUID`
- `projectId: UUID | null`
- `name: String`
- `status: TerrainJobStatus`
- `sourceBucket: String`
- `sourceObjectKey: String`
- `outputBucket: String`
- `outputPrefix: String`
- `crs: String`
- `bbox: Geometry / JSON`
- `minHeight: Double`
- `maxHeight: Double`
- `fileSize: Long`
- `errorMessage: String`
- `createdAt: Instant`
- `updatedAt: Instant`

#### `TerrainLayer`

Если нужен отдельный слой-реестр:

- `id`
- `jobId`
- `projectId`
- `title`
- `description`
- `terrainUrl`
- `status`
- `isVisible`
- `createdAt`

### 5.3. Статусы job

```text
NEW
UPLOADING
UPLOADED
QUEUED
PROCESSING
READY
FAILED
```

Дополнительно можно добавить:

- `CANCELLED`
- `DELETED`
- `ARCHIVED`

### 5.4. API endpoints

#### Upload

`POST /api/terrain/jobs`

Multipart upload GeoTIFF.

Ответ:

```json
{
  "id": "uuid",
  "status": "QUEUED",
  "name": "drone-dsm-2026-05",
  "createdAt": "..."
}
```

#### Получить статус

`GET /api/terrain/jobs/{id}`

#### Получить список terrain слоёв проекта

`GET /api/projects/{projectId}/terrain-layers`

#### Получить URL для Cesium

`GET /api/terrain/jobs/{id}/view`

Ответ:

```json
{
  "terrainUrl": "https://host/terrain/{jobId}/",
  "ready": true
}
```

### 5.5. Kafka events

События:

- `terrain.job.created`
- `terrain.job.queued`
- `terrain.job.processing`
- `terrain.job.ready`
- `terrain.job.failed`

Payload должен содержать минимум:

- `jobId`
- `sourceObjectKey`
- `outputPrefix`
- `projectId`
- `attempt`
- `timestamp`

---

## 6. План изменений в `geodata-service`

`geodata-service` стоит оставить как сервис каталога и связей, а не как генератор terrain.

### Что добавить:

1. Связь проекта с terrain-слоями.
2. Методы получения списка terrain по проекту.
3. Метаданные слоя:
   - имя,
   - дата создания,
   - статус,
   - extent,
   - CRS,
   - ссылка на готовый terrain.
4. При необходимости — сущность `RasterLayer` или `TerrainLayer` в доменной модели.

### Что не добавлять:

- тяжелую генерацию;
- прямое выполнение `ctb-tile`;
- долгие синхронные HTTP-запросы на build.

---

## 7. План интеграции worker на базе `tum-gis/cesium-terrain-builder-docker`

### 7.1. Роль worker

Worker должен:

1. Получить событие о новой задаче.
2. Скачать GeoTIFF из MinIO.
3. При необходимости выполнить нормализацию через GDAL.
4. Запустить `ctb-tile`.
5. Сгенерировать terrain в формате `quantized-mesh`.
6. Сохранить результат в public location.
7. Сообщить backend о готовности или ошибке.

### 7.2. Что делает образ

Образ `tum-gis/cesium-terrain-builder-docker` используется как контейнер для `ctb-tile` с поддержкой `quantized-mesh`. Он подходит именно как terrain worker, а не как frontend server.

### 7.3. Дополнительный wrapper

Для интеграции с Kafka и MinIO удобно добавить тонкий shell/Python wrapper поверх образа:

- забирает job из очереди;
- монтирует рабочую директорию;
- вызывает `gdalwarp` / `gdal_translate` при необходимости;
- вызывает `ctb-tile`;
- копирует результат в output bucket.

---

## 8. План docker-compose

### 8.1. Новые сервисы

Добавить в существующий compose:

- `terrain-service`
- `terrain-worker`
- `terrain-public` или публикацию через MinIO public bucket

### 8.2. Примерная схема

```yaml
services:
  postgres:
    image: postgis/postgis:16-3.4

  minio:
    image: minio/minio

  kafka:
    image: bitnami/kafka

  terrain-service:
    build: ./terrain-service

  terrain-worker:
    image: tum-gis/cesium-terrain-builder-docker:latest

  terrain-public:
    image: nginx:alpine
```

### 8.3. Что важно учесть

- `terrain-worker` должен иметь доступ к MinIO и Kafka.
- `terrain-public` должен отдавать `layer.json` и tile tree по HTTP.
- Для локальной разработки можно использовать bind mounts.
- Для production лучше раздавать готовые файлы через отдельный web root или S3-compatible public bucket.

---

## 9. Формат хранения файлов

### 9.1. Исходные файлы

```text
minio://terrain-input/{jobId}/source.tif
```

### 9.2. Промежуточные файлы

```text
/work/{jobId}/normalized.tif
/work/{jobId}/input.vrt
```

### 9.3. Готовый terrain

```text
minio://terrain-public/{jobId}/layer.json
minio://terrain-public/{jobId}/{z}/{x}/{y}.terrain
```

или

```text
/var/www/terrain/{jobId}/layer.json
/var/www/terrain/{jobId}/{z}/{x}/{y}.terrain
```

---

## 10. План worker script

### 10.1. Логика

1. Прочитать сообщение из Kafka.
2. Получить `jobId`.
3. Скачать `source.tif` из MinIO.
4. Привести к нужной проекции, если требуется.
5. Создать VRT, если GeoTIFF состоит из нескольких тайлов.
6. Запустить:

```bash
ctb-tile -f Mesh -C -N -o terrain <inputfile.tif or input.vrt>
```

7. Отдельным запуском создать `layer.json`, если это не сделано автоматически.
8. Загрузить результаты в public storage.
9. Отправить completion event.
10. Сообщить `terrain-service` через API или Kafka.

### 10.2. Обработка ошибок

Если генерация не удалась:

- сохранить stderr;
- обновить статус на `FAILED`;
- записать причину ошибки;
- добавить retry policy.

---

## 11. План интеграции с CesiumJS

### 11.1. Новый режим фронтенда

В frontend добавить отдельный экран:

- `2D Map` — текущий OpenLayers режим;
- `3D Terrain` — CesiumJS режим.

### 11.2. Загрузка terrain

Когда слой готов:

```js
const terrainProvider = await Cesium.CesiumTerrainProvider.fromUrl(terrainUrl);
const viewer = new Cesium.Viewer("cesiumContainer", {
  terrain: terrainProvider
});
```

### 11.3. UX сценарий

1. Пользователь загружает GeoTIFF.
2. В списке появляется job.
3. Статус обновляется в UI.
4. После статуса `READY` появляется кнопка `Open in 3D`.
5. CesiumJS открывает terrain.

### 11.4. Полезные элементы интерфейса

- прогресс-бар;
- статус job;
- карточка слоя;
- кнопка повторной генерации;
- кнопка удаления;
- выбор проекта.

---

## 12. Контроль качества данных

Перед генерацией terrain стоит проверить:

- CRS;
- наличие nodata;
- min/max elevation;
- size raster;
- количество каналов;
- тип данных;
- extent.

Если GeoTIFF не подходит, job должен завершаться с понятной ошибкой.

---

## 13. Рекомендуемые ограничения

### Для первого этапа

Поддержать только:

- single-band elevation GeoTIFF;
- DSM/DEM;
- один raster на один job;
- генерацию terrain без 3D mesh.

### Что отложить на потом

- photogrammetry mesh;
- point cloud pipeline;
- 3D Tiles;
- clip by polygon;
- multi-extent fusion;
- incremental rebuild.

---

## 14. Пошаговый план внедрения

### Этап 1. Подготовка инфраструктуры

- Добавить `terrain-service`.
- Добавить `terrain-worker`.
- Поднять отдельный bucket в MinIO.
- Добавить таблицы в PostgreSQL/PostGIS.
- Добавить Kafka topics.

### Этап 2. Backend API

- Реализовать upload endpoint.
- Реализовать get status endpoint.
- Реализовать job persistence.
- Реализовать публикацию событий.

### Этап 3. Worker

- Подключить `tum-gis/cesium-terrain-builder-docker`.
- Добавить wrapper script.
- Проверить генерацию на тестовом GeoTIFF.
- Научить worker сохранять `layer.json` и tiles.

### Этап 4. Frontend

- Добавить список terrain-слоёв.
- Добавить polling или WebSocket-обновление статуса.
- Добавить CesiumJS viewer.
- Подключить готовый terrain URL.

### Этап 5. Интеграция с проектами

- Привязать terrain к проектам.
- Показать terrain в карточке проекта.
- Добавить удаление и повторную генерацию.

### Этап 6. Hardening

- Retry policy.
- Dead letter queue.
- Лимиты на размер файла.
- Логирование и аудит.
- Health checks.

---

## 15. Acceptance criteria

Интеграция считается завершённой, если:

1. Пользователь может загрузить GeoTIFF.
2. Файл сохраняется в MinIO.
3. Создаётся job в БД.
4. Worker генерирует terrain.
5. Статус job меняется на `READY`.
6. CesiumJS открывает terrain без ручной подготовки файлов.
7. Повторный запуск не ломает систему.
8. Ошибки показываются пользователю понятным образом.

---

## 16. Риски и меры

### Риск: неподходящий GeoTIFF

Мера: проверка CRS, band count, nodata и размера до старта worker.

### Риск: долгие вычисления

Мера: асинхронная очередь, отдельный worker, таймауты и retry.

### Риск: слишком большие файлы

Мера: лимит размера upload, квоты на проект, pre-processing.

### Риск: нестабильная отдача terrain

Мера: хранить готовые артефакты в S3-совместимом storage или в статическом Nginx.

---

## 17. Итоговая схема

```text
Frontend (Vue + CesiumJS)
        ↓
API Gateway
        ↓
terrain-service (Spring Boot)
        ↓
MinIO + PostgreSQL/PostGIS + Kafka
        ↓
terrain-worker (tum-gis/cesium-terrain-builder-docker)
        ↓
quantized-mesh terrain
        ↓
public HTTP hosting
        ↓
CesiumJS terrain viewer
```

---

## 18. Рекомендуемое решение для текущего проекта

Для текущей версии GeoInfo System оптимальный путь такой:

- оставить существующую 2D карту без ломки;
- добавить отдельный terrain-контур;
- использовать `tum-gis/cesium-terrain-builder-docker` только как worker;
- хранить исходники и результат в MinIO;
- отдавать terrain по HTTP;
- подключать его в CesiumJS через `CesiumTerrainProvider.fromUrl()`.

Это даст быстрый и понятный MVP, который потом можно расширить до полноценной 3D GIS-платформы.

