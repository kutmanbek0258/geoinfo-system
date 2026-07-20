# REST API Справочник - ГеоИнфоСистема

## 1. Общие сведения

Вся работа с API системы происходит через **API Gateway**. Все запросы, за исключением публично открытых, должны содержать заголовок `Authorization: Bearer <access_token>`.

Access token можно получить у `auth-service`, используя OAuth2 `password` grant type.

## 2. Маршрутизация (API Gateway)

API Gateway предоставляет единую точку входа и перенаправляет запросы на соответствующие микросервисы:

| Префикс пути        | Целевой сервис      | Назначение                                    |
|---------------------|---------------------|-----------------------------------------------|
| `/api/auth/**`      | `auth-service`      | Аутентификация, регистрация, управление пользователями |
| `/api/geodata/**`   | `geodata-service`   | Управление проектами, слоями, гео-объектами, растрами, рельефом и стилями |
| `/api/documents/**` | `document-service`  | Управление документами и файлами              |
| `/api/search/**`    | `search-service`    | Полнотекстовый поиск                          |
| `/api/geo-abstraction/**` | `geoabstraction-service` | Оркестрация задач обработки растров/рельефов, ГИС-аналитика |
| `/raster/cog/**`     | `titiler`           | Прямой доступ к XYZ тайлам TiTiler (через S3)  |

---

## 3. Auth Service

Сервис для управления аутентификацией, пользователями, ролями и клиентами.

### Регистрация и пароли
- `POST /registration/` - **Public**: Регистрация нового пользователя.
- `POST /reset-password/` - **Public**: Инициировать сброс пароля.
- `POST /change-password/` - **Authenticated**: Сменить пароль для текущего пользователя.

### Управление аккаунтом
- `GET /account/` - **Authenticated**: Получить информацию о текущем пользователе.
- `POST /account/` - **Authenticated**: Обновить информацию о текущем пользователе.
- `GET /account/events` - **Authenticated**: Получить историю событий для текущего пользователя.
- `GET /account/sessions` - **Authenticated**: Получить активные сессии текущего пользователя.

### Администрирование
- `GET /admin/users` - **Authority: `USER_READ`**: Получить список всех пользователей.
- `POST /admin/users` - **Authority: `USER_CREATE`**: Создать нового пользователя.
- `GET /admin/users/{id}` - **Authority: `USER_READ`**: Получить пользователя по ID.
- `PUT /admin/users/{id}` - **Authority: `USER_UPDATE`**: Обновить пользователя по ID.
- `GET /admin/clients` - **Authority: `CLIENT_READ`**: Получить список OAuth2 клиентов.

### Прочее
- `GET /reference/roles` - **Authenticated**: Получить справочник всех ролей в системе.
- `GET /reference/authorities` - **Authenticated**: Получить справочник всех прав доступа.

---

## 4. GeoData Service

Сервис для управления векторными геоданными.

### Проекты (`/api/geodata/project`)
- `POST /` - **Authority: `GEO_PROJECT_CREATE`**: Создать новый проект.
- `GET /{id}` - **Authority: `GEO_PROJECT_READ`**: Получить проект по ID.
- `GET /page-query` - **Authority: `GEO_PROJECT_READ`**: Получить список проектов с пагинацией.
- `PUT /{id}` - **Authority: `GEO_PROJECT_UPDATE`**: Обновить проект.
- `DELETE /{id}` - **Authority: `GEO_PROJECT_DELETE`**: Удалить проект.
- `POST /{projectId}/share` - **Authority: `GEO_PROJECT_SHARE`**: Поделиться проектом с другим пользователем.

### Геообъекты (Точки, Линии, Полигоны)
Базовые пути: `/api/geodata/points`, `/api/geodata/multilines`, `/api/geodata/polygons`

- `POST /` - **Authority: `GEO_FEATURE_CREATE`**: Создать новый геообъект.
- `GET /` - **Authority: `GEO_FEATURE_READ`**: Получить все объекты с пагинацией.
- `GET /{id}` - **Authority: `GEO_FEATURE_READ`**: Получить объект по ID.
- `GET /by-project-id/{projectId}` - **Authority: `GEO_FEATURE_READ`**: Получить все объекты в проекте.
- `PUT /{id}` - **Authority: `GEO_FEATURE_UPDATE`**: Обновить геообъект.
- `DELETE /{id}` - **Authority: `GEO_FEATURE_DELETE`**: Удалить геообъект.
- `POST /{id}/upload-main-image` - **Authority: `GEO_FEATURE_UPDATE`**: Загрузить главное изображение для объекта.
- `GET /{id}/parts?minX&minY&maxX&maxY` - **Authority: `GEO_FEATURE_READ`**: Получить фрагменты геометрии (`ST_Dump`), попадающие в указанный BBox.
- `PATCH /{id}/parts` - **Authority: `GEO_FEATURE_UPDATE`**: Частично обновить мульти-геометрию (принимает список `subId` и `geojson`).

### Слои проекта (`/api/geodata/layers`)
- `POST /` - **Authority: `GEO_PROJECT_UPDATE`**: Создать новый слой проекта.
- `GET /{id}` - **Authority: `GEO_PROJECT_READ`**: Получить слой по ID.
- `PUT /{id}` - **Authority: `GEO_PROJECT_UPDATE`**: Обновить имя/характеристики слоя.
- `DELETE /{id}` - **Authority: `GEO_PROJECT_DELETE`**: Удалить слой (каскадно удаляет связанные объекты).
- `GET /by-project-id/{projectId}` - **Authority: `GEO_PROJECT_READ`**: Получить все слои проекта.

### Проектные растры (`/api/geodata/project-rasters`)
- `POST /` - **Authority: `GEO_PROJECT_UPDATE`**: Привязать обработанный растр к слою.
- `GET /{id}` - **Authority: `GEO_PROJECT_READ`**: Получить растр по ID.
- `PUT /{id}` - **Authority: `GEO_PROJECT_UPDATE`**: Обновить настройки растра.
- `DELETE /{id}` - **Authority: `GEO_PROJECT_DELETE`**: Удалить растр (стирает файл COG из MinIO).
- `GET /by-layer-id/{layerId}` - **Authority: `GEO_PROJECT_READ`**: Получить все растры слоя.

### Глобальные растровые подложки (`/api/geodata/raster-layers`)
- `POST /` - **Authority: `IMAGERY_LAYER_CREATE`**: Создать глобальную подложку.
- `GET /{id}` - **Authority: `IMAGERY_LAYER_READ`**: Получить подложку по ID.
- `PUT /{id}` - **Authority: `IMAGERY_LAYER_UPDATE`**: Обновить подложку.
- `DELETE /{id}` - **Authority: `IMAGERY_LAYER_DELETE`**: Удалить подложку.
- `GET /page-query` - **Authority: `IMAGERY_LAYER_READ`**: Получить список подложек с пагинацией.

### Стили растра (`/api/geodata/raster-style`)
- `GET /` - **Authority: `IMAGERY_LAYER_READ`**: Получить список всех стилей растров.
- `GET /{id}` - **Authority: `IMAGERY_LAYER_READ`**: Получить стиль по ID.
- `POST /` - **Authority: `IMAGERY_LAYER_CREATE`**: Создать кастомный стиль.
- `PUT /{id}` - **Authority: `IMAGERY_LAYER_UPDATE`**: Обновить существующий кастомный стиль.
- `DELETE /{id}` - **Authority: `IMAGERY_LAYER_DELETE`**: Удалить стиль.

### 3D слои рельефа (`/api/geodata/terrain-layers`)
- `POST /` - Создать запись рельефного слоя.
- `GET /{id}` - Получить рельеф по ID.
- `PUT /{id}` - Обновить рельеф.
- `DELETE /{id}` - Удалить рельеф (удаляет меш-файлы и COG в MinIO).
- `GET /` - Список слоев рельефа с пагинацией.

### 3D Tiles слои (`/api/geodata/3dtiles-layers`)
- `POST /` - Создать запись 3D Tiles слоя.
- `GET /{id}` - Получить 3D Tiles слой по ID.
- `PUT /{id}` - Обновить 3D Tiles слой.
- `DELETE /{id}` - Удалить 3D Tiles слой (удаляет тайлсет и b3dm файлы).
- `GET /` - Список слоев 3D Tiles с пагинацией.
- `GET /{id}/presigned-url` - Сгенерировать временную ссылку скачивания/чтения.

---

## 5. Document Service

Сервис для управления файлами и интеграции с OnlyOffice.

### Управление документами (`/api/documents`)
- `GET /geo/{geoObjectId}` - **Authority: `DOCUMENT_READ`**: Получить список документов для гео-объекта.
- `POST /` - **Authority: `DOCUMENT_CREATE`**: Загрузить новый документ (multipart/form-data).
- `GET /{documentId}/download` - **Authority: `DOCUMENT_READ`**: Скачать файл.
- `DELETE /{documentId}` - **Authority: `DOCUMENT_DELETE`**: Удалить документ.
- `POST /{documentId}` - **Authority: `DOCUMENT_UPDATE`**: Обновить метаданные документа.
- `GET /{documentId}/presigned-url` - **Authority: `DOCUMENT_READ`**: Сгенерировать временную ссылку на файл.
- `GET /public/image/{documentId}` - **Public**: Получить публичный файл-изображение.

### Интеграция с OnlyOffice (`/api/documents`)
- `GET /{documentId}/onlyoffice-config` - **Authority: `DOCUMENT_READ`**: Получить конфигурацию для редактора OnlyOffice.
- `POST /onlyoffice-callback/{documentId}` - **Public (защищено JWT OnlyOffice)**: Callback от OnlyOffice для сохранения изменений.
- `GET /content/{documentId}` - **Public (защищено JWT OnlyOffice)**: Для получения документа в OnlyOffice.

---

## 6. GeoAbstraction Service

Сервис для оркестрации фоновых задач обработки тяжелых растровых данных и рельефа.

### Рельеф (Terrain)
- `POST /api/geo-abstraction/terrain/upload` - Загрузить DEM (GeoTIFF) для генерации 3D-рельефа. Параметры: `name`, `file`, `projectId` (optional).

### Задачи (`/api/geo-abstraction/jobs`)
- `POST /` - Создать задачу `TERRAIN_MESH`. Параметры: `name`, `file`, `projectId` (optional).
- `GET /` - Получить список всех задач. Поддерживает фильтр `?projectId=<uuid>`.
- `GET /{id}` - Получить статус и детали задачи.
- `POST /confirm` - Подтвердить загрузку файла и запустить задачу. Параметры: `name`, `objectKey`, `fileSize`, `taskType`, `projectId` (optional).

### Sentinel-2 / Landsat 8 (`/api/geo-abstraction/sentinel`, `/api/geo-abstraction/landsat`)
- `POST /upload` - Загрузить архив (.zip/.tar.gz) и создать задачу `SENTINEL_COG` или `LANDSAT_COG`. Параметры: `name`, `file`, `channels`, `indexType`, `projectId` (optional).


---

## 7. Search Service

Сервис для полнотекстового поиска.

### Поиск (`/api/search`)
- `GET /all` - **Authenticated**: Выполнить глобальный поиск по всем данным.
- `GET /geo` - **Authenticated**: Выполнить поиск только по гео-объектам с фильтрацией по типу.
