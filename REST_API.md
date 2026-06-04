# REST API Справочник - ГеоИнфоСистема

## 1. Общие сведения

Вся работа с API системы происходит через **API Gateway**. Все запросы, за исключением публично открытых, должны содержать заголовок `Authorization: Bearer <access_token>`.

Access token можно получить у `auth-service`, используя OAuth2 `password` grant type.

## 2. Маршрутизация (API Gateway)

API Gateway предоставляет единую точку входа и перенаправляет запросы на соответствующие микросервисы:

| Префикс пути        | Целевой сервис      | Назначение                                    |
|---------------------|---------------------|-----------------------------------------------|
| `/api/auth/**`      | `auth-service`      | Аутентификация, регистрация, управление пользователями |
| `/api/geodata/**`   | `geodata-service`   | Управление гео-объектами и проектами          |
| `/api/documents/**` | `document-service`  | Управление документами и файлами              |
| `/api/search/**`    | `search-service`    | Полнотекстовый поиск                          |
| `/api/geo-abstraction/**` | `geoabstraction-service` | Обработка рельефа и Sentinel-2 |
| `/geoserver/**`     | `geoserver`         | Прямой доступ к WMS/WFS слоям GeoServer        |

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

Сервис для централизованного управления растровыми данными, рельефом и интеграции с GeoServer.

### Слои снимков (`/api/geo-abstraction/imagery-layer`)
*Legacy-маршрут `/api/geodata/imagery-layer` перенаправляется сюда автоматически.*

- `POST /` - **Authority: `IMAGERY_LAYER_CREATE`**: Сохранить метаданные нового слоя.
- `GET /{id}` - **Authority: `IMAGERY_LAYER_READ`**: Получить метаданные слоя.
- `PUT /{id}` - **Authority: `IMAGERY_LAYER_UPDATE`**: Обновить метаданные слоя.
- `DELETE /{id}` - **Authority: `IMAGERY_LAYER_DELETE`**: Удалить метаданные слоя.
- `GET /page-query` - **Authority: `IMAGERY_LAYER_READ`**: Получить список слоев с пагинацией.
- `POST /upload` - **Authority: `IMAGERY_LAYER_CREATE`**: Загрузить произвольный GeoTIFF для оптимизации (COG) и авто-публикации.

### Рельеф (Terrain)
- `POST /api/geo-abstraction/terrain/upload` - Загрузить DEM (GeoTIFF) для генерации 3D-рельефа.

### Задачи (`/api/geo-abstraction/jobs`)
- `POST /` - Создать задачу `TERRAIN_MESH`.
- `GET /` - Получить список всех задач.
- `GET /{id}` - Получить статус и детали задачи.

### Sentinel-2 / Landsat 8 (`/api/geo-abstraction/sentinel`, `/api/geo-abstraction/landsat`)
- `POST /upload` - Загрузить архив (.zip/.tar.gz) и создать задачу `SENTINEL_COG` или `LANDSAT_COG`. Параметры: `name`, `file`, `channels`, `indexType`.

### Готовые 3D слои (`/api/geo-abstraction/layers`)
- `GET /` - Список готовых 3D слоев рельефа.
- `DELETE /{id}` - Удалить слой.


---

## 7. Search Service

Сервис для полнотекстового поиска.

### Поиск (`/api/search`)
- `GET /all` - **Authenticated**: Выполнить глобальный поиск по всем данным.
- `GET /geo` - **Authenticated**: Выполнить поиск только по гео-объектам с фильтрацией по типу.
