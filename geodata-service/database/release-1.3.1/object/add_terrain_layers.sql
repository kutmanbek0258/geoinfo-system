--liquibase formatted sql

--changeset admin:create_terrain_layers_table
CREATE TABLE geodata.terrain_layers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id             UUID,
    output_prefix      VARCHAR(512),
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    terrain_url        VARCHAR(512),
    cog_object_key     VARCHAR(512),
    status             VARCHAR(50),
    is_visible         BOOLEAN DEFAULT TRUE,
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
