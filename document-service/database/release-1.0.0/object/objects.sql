-- =====================================================================
--  СХЕМА БАЗЫ ДАННЫХ: Document Service
--  Версия: v4-final
--  Требования: PostgreSQL 13+, расширение pgcrypto (для UUID)
--  Роль: Хранение метаданных файлов, расположенных в MinIO
-- =====================================================================

-- ---------- БАЗОВЫЕ НАСТРОЙКИ / РАСШИРЕНИЯ ----------
-- Создание схемы для изоляции таблиц Document Service
CREATE SCHEMA IF NOT EXISTS documents;

-- Установка расширения для генерации UUID (gen_random_uuid())
CREATE EXTENSION IF NOT EXISTS pgcrypto;

SET search_path = documents, public;

-- =====================================================================
--  ДОКУМЕНТЫ И МЕТАДАННЫЕ
-- =====================================================================

-- -------------------- DOCUMENTS (Метаданные Документов) --------------------
-- Основная таблица для всех записей о документах.
CREATE TABLE IF NOT EXISTS documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- ID геообъекта, к которому привязан документ. Связь с Geo Data Service.
    geo_object_id           UUID NOT NULL,
    file_name               VARCHAR(255) NOT NULL,
    -- Ключ объекта в MinIO. Используется для скачивания/удаления бинарного файла.
    minio_object_key        VARCHAR(255) NOT NULL UNIQUE,
    mime_type               VARCHAR(100) NOT NULL,
    file_size_bytes         BIGINT NOT NULL,
    description             TEXT,
    is_latest_version       BOOLEAN DEFAULT TRUE,

    -- Audit Fields
    created_by              VARCHAR(255),
    created_date            TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP
);
-- Индекс для быстрого поиска всех документов, привязанных к конкретному геообъекту
CREATE INDEX IF NOT EXISTS ix_documents_geo_object_id ON documents(geo_object_id);

-- -------------------- TAGS (Справочник Тегов) --------------------
-- Список уникальных тегов, используемых в системе (например, "Протокол", "Смета").
CREATE TABLE IF NOT EXISTS tags (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(50) NOT NULL UNIQUE
);

-- -------------------- DOCUMENT_TAG_LINK (Связь Тегов и Документов) --------------------
-- Таблица "многие-ко-многим" для связи документов и тегов.
CREATE TABLE IF NOT EXISTS document_tag_link (
    document_id             UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tag_id                  BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    -- Составной первичный ключ обеспечивает уникальность пары (документ, тег)
    PRIMARY KEY (document_id, tag_id)
);

-- =====================================================================
--  ВОЗВРАТ search_path
-- =====================================================================
SET search_path = public;
