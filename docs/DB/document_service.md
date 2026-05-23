## **Финальная Схема Базы Данных PostgreSQL для Document Service**

Данная схема предназначена для хранения метаданных файлов, которые физически располагаются в MinIO, и обеспечения связи с геообъектами.

### **1. Таблица documents (Метаданные Документов)**

Основная таблица для всех записей о документах.

| Колонка | Тип данных | Описание | Ограничения |
| :--- | :--- | :--- | :--- |
| id | UUID | Первичный ключ | DEFAULT gen_random_uuid() |
| geo_object_id | UUID | ID геообъекта (Point/Line/Polygon) | NOT NULL |
| file_name | VARCHAR(255) | Оригинальное имя файла | NOT NULL |
| minio_object_key | VARCHAR(255) | Ключ объекта в MinIO | NOT NULL, UNIQUE |
| mime_type | VARCHAR(100) | MIME-тип файла | NOT NULL |
| file_size_bytes | BIGINT | Размер в байтах | NOT NULL |
| description | TEXT | Описание | |
| is_latest_version | BOOLEAN | Флаг актуальной версии | DEFAULT TRUE |
| created_by | VARCHAR(255) | Автор записи | |
| created_date | TIMESTAMP | Дата создания | |
| last_modified_by | VARCHAR(255) | Кто изменил | |
| last_modified_date | TIMESTAMP | Дата изменения | |

### **2. Таблица tags (Справочник Тегов)**

| Колонка | Тип данных | Описание | Ограничения |
| :--- | :--- | :--- | :--- |
| id | BIGINT | Первичный ключ | SERIAL |
| name | VARCHAR(50) | Название тега | NOT NULL, UNIQUE |

### **3. Таблица document_tag_link (Связь Тегов и Документов)**

| Колонка | Тип данных | Описание | Ограничения |
| :--- | :--- | :--- | :--- |
| document_id | UUID | ID документа | FOREIGN KEY |
| tag_id | BIGINT | ID тега | FOREIGN KEY |
