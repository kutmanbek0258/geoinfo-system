# Document Service

Микросервис для управления документами и их метаданными в составе системы GeoInfoSystem.  
Document Service обеспечивает загрузку, хранение, выдачу, редактирование и удаление документов, интеграцию с MinIO (файловое хранилище) и OnlyOffice (онлайн-редактирование), а также хранение метаданных в PostgreSQL.

---

## Основные возможности

- Загрузка документов (PDF, DOCX, XLSX, изображения и др.) через API.
- Хранение файлов в MinIO (S3-совместимое объектное хранилище).
- Хранение и управление метаданными документов (имя, тип, размеры, описание, теги, связь с гео-объектами).
- Получение списка всех документов, связанных с определённым гео-объектом.
- Получение метаданных и скачивание файла по documentId.
- Генерация временных (presigned) ссылок для прямого доступа к файлам (например, для OnlyOffice).
- Получение конфига для OnlyOffice (режимы просмотра и редактирования).
- Callback endpoint для сохранения изменений из OnlyOffice обратно в MinIO.
- Удаление документов и их файлов.
- Обновление метаданных (описание, теги и др.).
- Авторизация на основе OAuth2/JWT, интеграция с Eureka Discovery и Config Server.

---

## Архитектура и компоненты

- **Spring Boot** — основной фреймворк приложения.
- **PostgreSQL** — хранение метаданных документов и тегов.
- **MinIO** — объектное файловое хранилище (S3-совместимый API).
- **OnlyOffice** — онлайн-редактирование офисных документов, интеграция через API и callback.
- **Spring Data JPA** — работа с БД.
- **Spring Security** — авторизация и разграничение доступа.
- **Docker** — контейнеризация сервиса.
- **Kafka** — интеграция для событий и поиска (опционально).

---

## Основные эндпоинты API

Базовый путь: `/api/documents`

| Метод   | Путь                                      | Описание                                                 |
|---------|-------------------------------------------|----------------------------------------------------------|
| GET     | `/geo/{geoObjectId}`                      | Получить все документы для указанного гео-объекта        |
| POST    | `/`                                       | Загрузить новый документ (multipart/form-data)           |
| GET     | `/{documentId}/download`                  | Скачать бинарный файл документа                          |
| GET     | `/{documentId}/presigned-url`             | Получить presigned URL на файл для прямого доступа       |
| GET     | `/{documentId}/onlyoffice-config`         | Получить конфиг для OnlyOffice (режимы view/edit)        |
| POST    | `/{documentId}/onlyoffice-callback`       | Callback OnlyOffice для сохранения изменений             |
| DELETE  | `/{documentId}`                           | Удалить документ и файл                                  |
| PUT     | `/{documentId}`                           | Обновить метаданные документа (описание, теги)           |

---

## Пример загрузки документа

**POST** `/api/documents`

- `multipart/form-data` с полями:
    - `geoObjectId` (UUID) — ID гео-объекта
    - `description` (String) — описание документа
    - `tags` (Set<String>) — теги
    - `file` (File) — загружаемый файл

---

## Пример получения presigned URL

**GET** `/api/documents/{documentId}/presigned-url?expiresInSeconds=600`

Ответ:
```json
{
  "url": "https://minio.example.com/documents/abc123?X-Amz-...",
  "expiresInSeconds": 600
}
```
Используйте эту ссылку для прямого доступа к файлу (например, в OnlyOffice).

---

## Интеграция с OnlyOffice

1. **Получение конфига**
    - **GET** `/api/documents/{documentId}/onlyoffice-config?mode=edit&userId=123&userName=Ivan`
    - Возвращает JSON-конфиг для инициализации OnlyOffice на фронте.

2. **Callback**
    - **POST** `/api/documents/{documentId}/onlyoffice-callback`
    - OnlyOffice отправляет результат редактирования, сервис обновляет файл в MinIO.

---

## Конфигурация через переменные окружения

- **PostgreSQL**:
    - `spring.datasource.url`
    - `spring.datasource.username`
    - `spring.datasource.password`
- **MinIO**:
    - `minio.endpoint`
    - `minio.access-key`
    - `minio.secret-key`
    - `minio.bucket`
- **OnlyOffice**:
    - `onlyoffice.callback-base-url` — базовый url для callback'а

Для production и dev окружения используйте Spring Cloud Config Server или стандартные переменные.

---

## Пример docker-compose (фрагмент)

```yaml
services:
  document-service:
    build: ./document-service
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_URL=jdbc:postgresql://postgres-docs:5432/docs_db
      - DB_USER=postgres
      - DB_PASS=password
      - MINIO_ENDPOINT=http://minio:9000
      - MINIO_ACCESS_KEY=minioadmin
      - MINIO_SECRET_KEY=minioadmin
      - MINIO_BUCKET=documents
      - ONLYOFFICE_CALLBACK_BASE_URL=http://document-service/api/documents
    depends_on:
      - postgres-docs
      - minio
      - onlyoffice-doc-server
```

---

## Безопасность

- Все методы защищены OAuth2/JWT, доступ только для авторизованных пользователей.
- Генерируемые ссылки (presigned url) живут ограниченное время (по умолчанию 5-10 минут).
- Callback endpoint OnlyOffice можно защитить секретом или проверкой подписи.

---

## Разработка и запуск

- Соберите сервис:
  ```bash
  mvn clean install
  ```
- Запустите с помощью Docker Compose или напрямую (указав конфиги).
- Для локальной разработки настройте application.yml с параметрами подключения к PostgreSQL и MinIO.

---

## Контакты и поддержка

- Вопросы и баги — через GitHub Issues.
- Предложения по улучшению — pull requests приветствуются!

---