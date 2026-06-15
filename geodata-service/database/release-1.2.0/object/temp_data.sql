--liquibase formatted sql

--changeset CLI:temp-analysis-init-1
--validCheckSum: any
SET search_path = geodata, public;

CREATE TABLE geodata.temp_analysis_geometries (
    id BIGSERIAL PRIMARY KEY,
    task_id UUID NOT NULL,
    geom GEOMETRY(Geometry, 4326),
    properties JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_temp_geom_task_id ON geodata.temp_analysis_geometries(task_id);
CREATE INDEX idx_temp_geom_gist ON geodata.temp_analysis_geometries USING GIST(geom);
