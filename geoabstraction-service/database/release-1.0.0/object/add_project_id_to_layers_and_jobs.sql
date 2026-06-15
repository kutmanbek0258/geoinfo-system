--liquibase formatted sql

--changeset admin:add_project_id_to_layers_and_jobs
-- Add project_id column to imagery_layers, terrain_layers and geo_abstract_jobs
ALTER TABLE geoabstraction.imagery_layers ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE geoabstraction.terrain_layers ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE geoabstraction.geo_abstract_jobs ADD COLUMN IF NOT EXISTS project_id UUID;

-- Create indexes for project_id to improve performance
CREATE INDEX IF NOT EXISTS ix_imagery_layers_project_id ON geoabstraction.imagery_layers(project_id);
CREATE INDEX IF NOT EXISTS ix_terrain_layers_project_id ON geoabstraction.terrain_layers(project_id);
CREATE INDEX IF NOT EXISTS ix_geo_abstract_jobs_project_id ON geoabstraction.geo_abstract_jobs(project_id);
