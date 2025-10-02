```markdown
# GeoInfo System REST API

Этот документ описывает REST API для микросервисов GeoInfo System. Документация сгенерирована на основе анализа Java-кода контроллеров и DTO.

---

## 1. Geodata Service

Префикс: `/api/geodata`

### 1.1. Projects

Ресурс для управления проектами.

**Контроллер:** `ProjectController.java`

#### `GET /project/page-query`

Получение списка проектов с пагинацией и фильтрацией.

- **Query Parameters:**
  - `page` (number, optional): Номер страницы.
  - `size` (number, optional): Размер страницы.
  - `sort` (string, optional): Поле для сортировки, например `createdDate,desc`.
  - Другие поля из `ProjectDto` могут использоваться для фильтрации.

- **Response (200 OK):** `Page<ProjectDto>`
  ```json
  {
    "content": [
      {
        "id": "uuid",
        "name": "string",
        "description": "string"
      }
    ],
    "totalElements": "number",
    "totalPages": "number",
    "size": "number",
    "number": "number"
  }
  ```

#### `POST /project`

Создание нового проекта.

- **Request Body:** `ProjectDto`
  ```json
  {
    "name": "string",
    "description": "string"
  }
  ```

- **Response (200 OK):** Пустое тело.

#### `GET /project/{id}`

Получение проекта по ID.

- **Path Variables:**
  - `id` (uuid): ID проекта.
- **Response (200 OK):** `ProjectDto`

#### `PUT /project/{id}`

Обновление проекта.

- **Path Variables:**
  - `id` (uuid): ID проекта.
- **Request Body:** `ProjectDto`
- **Response (200 OK):** Пустое тело.

#### `DELETE /project/{id}`

Удаление проекта.

- **Path Variables:**
  - `id` (uuid): ID проекта.
- **Response (200 OK):** Пустое тело.

---

### 1.2. Imagery Layers

Ресурс для управления слоями изображений.

**Контроллер:** `ImageryLayerController.java`

#### `GET /imagery-layer/page-query`

Получение списка слоев с пагинацией.

- **Response (200 OK):** `Page<ImageryLayerDto>` (структура аналогична проектам).

#### `POST /imagery-layer`

Создание нового слоя.

- **Request Body:** `ImageryLayerDto`
- **Response (200 OK):** Пустое тело.

*...и так далее для GET, PUT, DELETE по аналогии с Projects...*

---

### 1.3. Vector Geodata (Points, Multilines, Polygons)

**Контроллеры:** `ProjectPointController.java`, `ProjectMultilineController.java`, `ProjectPolygonController.java`

#### `POST /points`

Создание новой точки.

- **Request Body:** `CreateProjectPointDto`
  ```json
  {
    "projectId": "uuid",
    "name": "string",
    "description": "string",
    "status": "string (ACTIVE, INACTIVE, ...)",
    "geom": "GeoJSON Point object"
  }
  ```
- **Response (201 Created):** `ProjectPointDto`

#### `GET /points/by-project-id/{projectId}`

Получение всех точек для проекта.

- **Path Variables:**
  - `projectId` (uuid): ID проекта.
- **Response (200 OK):** `Page<ProjectPointDto>`

*Эндпоинты `GET /{id}`, `PUT /{id}`, `DELETE /{id}` реализованы для `/points`, `/multilines`, `/polygons` по аналогии.*

---

## 2. Document Service

Префикс: `/api/documents`

### 2.1. Documents

**Контроллер:** `DocumentController.java`

#### `GET /geo/{geoObjectId}`

Получение всех документов для гео-объекта.

- **Path Variables:**
  - `geoObjectId` (uuid): ID гео-объекта.
- **Response (200 OK):** `List<DocumentDto>`
  ```json
  [
    {
      "id": "uuid",
      "geoObjectId": "uuid",
      "fileName": "string",
      "mimeType": "string",
      "fileSizeBytes": "number",
      "description": "string",
      "uploadedByUserId": "uuid",
      "uploadDate": "string (date-time)",
      "isLatestVersion": "boolean",
      "tags": [ { "id": "number", "name": "string" } ]
    }
  ]
  ```

#### `POST /`

Загрузка нового документа.

- **Request (multipart/form-data):**
  - `geoObjectId` (uuid)
  - `description` (string)
  - `tags` (Set<string>)
  - `file` (file)
- **Response (201 Created):** `DocumentDto`

#### `GET /{documentId}/download`

Скачивание файла.

- **Response (200 OK):** `byte[]` (бинарный файл).

#### `DELETE /{documentId}`

Удаление документа.

- **Response (204 No Content):** Пустое тело.

#### `PUT /{documentId}`

Обновление метаданных документа.

- **Request Body:** `UpdateDocumentRequest`
  ```json
  {
    "description": "string",
    "tags": ["string"]
  }
  ```
- **Response (200 OK):** `DocumentDto`

#### `GET /{documentId}/presigned-url`

Получение временной ссылки на файл.

- **Query Parameters:**
  - `expiresInSeconds` (long, optional, default: 300)
- **Response (200 OK):** `PresignedUrlResponse`

### 2.2. OnlyOffice Integration

**Контроллер:** `OnlyOfficeController.java`

#### `GET /{documentId}/onlyoffice-config`

Получение конфигурации для редактора OnlyOffice.

- **Query Parameters:**
  - `mode` (string, optional, default: 'view'): 'view' или 'edit'.
  - `userId` (string)
  - `userName` (string)
- **Response (200 OK):** `OnlyOfficeConfig`

#### `POST /{documentId}/onlyoffice-callback`

Callback от сервера OnlyOffice после сохранения документа.

- **Request Body:** `OnlyOfficeCallback`
- **Response (200 OK):** Пустое тело.

---

## 3. Search Service

Префикс: `/api/search`

**Контроллер:** `SearchController.java`

#### `GET /`

Выполнение поиска по всем данным.

- **Query Parameters:**
  - `query` (string): Поисковый запрос.
  - `page`, `size`, `sort` (стандартные для пагинации).
- **Response (200 OK):** `Page<Map>` (гибкая структура, возвращаемая Elasticsearch).

```
