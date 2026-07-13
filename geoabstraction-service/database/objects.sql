--liquibase formatted sql

--changeset admin:create_geoabstraction_objects splitStatements:false
CREATE SCHEMA IF NOT EXISTS geoabstraction;
CREATE SCHEMA IF NOT EXISTS analysis;

-- -------------------- GEO ABSTRACT JOBS --------------------
CREATE TABLE IF NOT EXISTS geoabstraction.geo_abstract_jobs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID,
    name                    VARCHAR(255) NOT NULL,
    status                  VARCHAR(50) NOT NULL,
    task_type               VARCHAR(50),
    characteristics         JSONB DEFAULT '{}',
    source_bucket           VARCHAR(255),
    source_object_key       VARCHAR(255),
    output_bucket           VARCHAR(255),
    output_prefix           VARCHAR(255),
    crs                     VARCHAR(50),
    bbox                    GEOMETRY(MultiPolygon, 4326),
    min_height              DOUBLE PRECISION,
    max_height              DOUBLE PRECISION,
    file_size               BIGINT,
    error_message           TEXT,
    -- Audit Fields
    created_by              VARCHAR(255),
    created_date            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_geo_abstract_jobs_bbox ON geoabstraction.geo_abstract_jobs USING GIST (bbox);

-- -------------------- ANALYSIS TASKS --------------------
CREATE TABLE IF NOT EXISTS analysis.analysis_tasks (
    id                      UUID PRIMARY KEY,
    plugin_name             VARCHAR(100) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    input_params            JSONB DEFAULT '{}',
    s3_input_paths          JSONB DEFAULT '{}',
    s3_output_paths         JSONB DEFAULT '{}',
    error_message           TEXT,
    user_id                 UUID,
    project_id              UUID,
    -- Audit Fields
    created_by              VARCHAR(255),
    created_date            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_analysis_tasks_status ON analysis.analysis_tasks(status);
CREATE INDEX IF NOT EXISTS idx_analysis_tasks_user_id ON analysis.analysis_tasks(user_id);

-- -------------------- PLUGIN SCHEMAS --------------------
CREATE TABLE IF NOT EXISTS analysis.plugin_schemas (
    plugin_name             VARCHAR(100) PRIMARY KEY,
    title                   VARCHAR(255) NOT NULL,
    icon                    VARCHAR(100),
    schema                  JSONB NOT NULL DEFAULT '{}'::jsonb,
    registered_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
