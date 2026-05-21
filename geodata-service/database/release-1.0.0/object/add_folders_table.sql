-- liquibase formatted sql

-- changeset kutman:add_folders_table
CREATE TABLE geodata.folders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES geodata.projects(id) ON DELETE CASCADE,
    parent_id    UUID REFERENCES geodata.folders(id) ON DELETE SET NULL,
    name         VARCHAR(256) NOT NULL,
    description  TEXT,
    characteristics JSONB,
    created_by        VARCHAR(255),
    created_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by  VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE geodata.project_points ADD COLUMN folder_id UUID REFERENCES geodata.folders(id) ON DELETE SET NULL;
ALTER TABLE geodata.project_multilines ADD COLUMN folder_id UUID REFERENCES geodata.folders(id) ON DELETE SET NULL;
ALTER TABLE geodata.project_polygons ADD COLUMN folder_id UUID REFERENCES geodata.folders(id) ON DELETE SET NULL;

CREATE INDEX ix_folders_project ON geodata.folders(project_id);
CREATE INDEX ix_folders_parent ON geodata.folders(parent_id);
CREATE INDEX ix_pp_folder ON geodata.project_points(folder_id);
CREATE INDEX ix_pm_folder ON geodata.project_multilines(folder_id);
CREATE INDEX ix_pg_folder ON geodata.project_polygons(folder_id);
