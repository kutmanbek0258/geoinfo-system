# Сервис Документов (Document Service)

## Обзор

Этот сервис отвечает за управление документами и их метаданными в рамках системы GeoInfoSystem. Он полностью изолирован и не имеет прямых зависимостей от других сервисов на уровне базы данных. Связь с гео-объектами из `geodata-service` осуществляется по полю `geoObjectId` без использования внешних ключей.

Сервис выступает в роли посредника между клиентскими приложениями (фронтендом) и файловым хранилищем MinIO. Он обрабатывает загрузку, скачивание и удаление файлов, сохраняя связанную метаинформацию в собственной базе данных PostgreSQL.

Аудит (запись информации о создании и изменении) выполняется автоматически.

Сервис является приложением Spring Boot, настроенным как OAuth2 Resource Server и регистрирующимся в Eureka Discovery Server.

---

## API Эндпоинты

Следующие эндпоинты доступны по базовому пути `/api/documents`:

| Метод  | Путь                               | Описание                                                      |
|--------|------------------------------------|---------------------------------------------------------------|
| `GET`    | `/geo/{geoObjectId}`               | Получить метаданные всех документов для указанного гео-объекта. |
| `POST`   | `/`                                | Загрузить новый документ. (Multipart запрос)                  |
| `GET`    | `/{documentId}/download`           | Скачать бинарный файл указанного документа.                   |
| `DELETE` | `/{documentId}`                    | Удалить документ и его файл из хранилища.                     |
| `PUT`    | `/{documentId}`                    | Обновить метаданные (описание, теги) документа.               |

**Пример тела ответа (JSON) для одного документа:**
```json
{
  "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "geoObjectId": "f0e9d8c7-b6a5-4321-fedc-ba9876543210",
  "fileName": "report.pdf",
  "mimeType": "application/pdf",
  "fileSizeBytes": 123456,
  "description": "Ежегодный отчет",
  "tags": [
    { "id": 1, "name": "отчет" },
    { "id": 2, "name": "2024" }
  ],
  "isLatestVersion": true,
  "createdBy": "user@example.com",
  "createdDate": "2024-09-28T10:30:00Z",
  "lastModifiedBy": "user@example.com",
  "lastModifiedDate": "2024-09-28T10:30:00Z"
}
```

**Пример запроса на загрузку (`POST /api/documents`):**

Это `multipart/form-data` запрос со следующими частями:
- `geoObjectId`: (UUID) ID гео-объекта, с которым связывается документ.
- `description`: (String) Описание документа.
- `tags`: (Set<String>) Набор тегов.
- `file`: (File) Загружаемый файл документа.

---

## Конфигурация

В Docker-окружении сервис настраивается через Spring Cloud Config Server. Для локальной разработки можно использовать файл `src/main/resources/application.yml`.

### Обязательные параметры

Для работы сервиса необходимо настроить следующие свойства:

```yaml
# Подключение к PostgreSQL
spring:
  datasource:
    url: jdbc:postgresql://<host>:<port>/<database_name>
    username: <db_user>
    password: <db_password>

# Подключение к файловому хранилищу MinIO
minio:
  endpoint: http://<minio_host>:<minio_port>
  access-key: <your_minio_access_key>
  secret-key: <your_minio_secret_key>
  bucket: <bucket_name_for_documents> # например, documents
```

При запуске через `docker-compose.yml` подключение к БД настраивается через переменные окружения (`DB_URL`, `DB_USER`, `DB_PASS`), а подключение к MinIO должно быть задано в центральном репозитории Config Server.

---

## Как запустить

### Локальная разработка

1.  Убедитесь, что у вас запущены PostgreSQL и MinIO.
2.  Настройте параметры подключения в файле `src/main/resources/application.yml`.
3.  Запустите главный класс `DocumentServiceApplication.java` из вашей IDE.

### Docker

1.  Убедитесь, что центральный репозиторий конфигурации (`https://github.com/kutmanbek0258/cloud-configs`) содержит корректную конфигурацию для `document-service.yml`.
2.  Выполните команду `docker-compose up` из корневой директории проекта.