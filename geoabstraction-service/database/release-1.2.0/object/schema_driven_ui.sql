--liquibase formatted sql

--changeset CLI:plugin-schemas-init
CREATE TABLE IF NOT EXISTS analysis.plugin_schemas (
    plugin_name VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    icon VARCHAR(100),
    schema JSONB NOT NULL,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
