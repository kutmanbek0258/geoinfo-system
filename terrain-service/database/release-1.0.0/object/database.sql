--liquibase formatted sql

--changeset daivanov:terrain-init-1
CREATE SCHEMA IF NOT EXISTS terrain;

SET search_path = terrain, public;

CREATE TABLE terrain.terrain_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    source_bucket VARCHAR(255),
    source_object_key VARCHAR(255),
    output_bucket VARCHAR(255),
    output_prefix VARCHAR(255),
    crs VARCHAR(50),
    bbox GEOMETRY(Polygon, 4326),
    min_height DOUBLE PRECISION,
    max_height DOUBLE PRECISION,
    file_size BIGINT,
    error_message TEXT,
    -- Audit Fields
    created_by              VARCHAR(255),
    created_date            TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP
);

CREATE TABLE terrain.terrain_layers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES terrain.terrain_jobs(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    terrain_url VARCHAR(512),
    status VARCHAR(50),
    is_visible BOOLEAN DEFAULT TRUE,
    -- Audit Fields
    created_by              VARCHAR(255),
    created_date            TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP
);

-- Index for spatial queries
CREATE INDEX idx_terrain_jobs_bbox ON terrain.terrain_jobs USING GIST (bbox);
