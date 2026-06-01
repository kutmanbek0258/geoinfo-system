--liquibase formatted sql

--changeset admin:create_mvt_views
-- View for Points
CREATE OR REPLACE VIEW geodata.v_project_points_mvt AS
SELECT
    id,
    project_id,
    folder_id,
    name,
    status,
    characteristics,
    geom
FROM geodata.project_points
WHERE geom IS NOT NULL;

-- View for Lines
CREATE OR REPLACE VIEW geodata.v_project_multilines_mvt AS
SELECT
    id,
    project_id,
    folder_id,
    name,
    status,
    characteristics,
    geom
FROM geodata.project_multilines
WHERE geom IS NOT NULL;

-- View for Polygons
CREATE OR REPLACE VIEW geodata.v_project_polygons_mvt AS
SELECT
    id,
    project_id,
    folder_id,
    name,
    status,
    characteristics,
    geom
FROM geodata.project_polygons
WHERE geom IS NOT NULL;
