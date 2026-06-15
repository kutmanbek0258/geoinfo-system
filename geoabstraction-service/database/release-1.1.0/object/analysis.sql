--liquibase formatted sql

--changeset CLI:analysis-init-1
CREATE SCHEMA IF NOT EXISTS analysis;

SET search_path = analysis, public;

CREATE TABLE analysis.analysis_tasks (
    id UUID PRIMARY KEY,
    plugin_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_params JSONB,
    s3_input_paths JSONB,
    s3_output_paths JSONB,
    error_message TEXT,
    user_id UUID,
    project_id UUID,
    -- Audit Fields
    created_by              VARCHAR(255),
    created_date            TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP
);

CREATE INDEX idx_analysis_tasks_status ON analysis.analysis_tasks(status);
CREATE INDEX idx_analysis_tasks_user_id ON analysis.analysis_tasks(user_id);
