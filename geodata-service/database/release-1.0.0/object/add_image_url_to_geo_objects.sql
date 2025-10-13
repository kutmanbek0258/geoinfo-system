-- liquibase formatted sql

-- changeset ebt32945:20251013-1
ALTER TABLE geodata.project_points ADD COLUMN image_url VARCHAR(1000);
COMMENT ON COLUMN geodata.project_points.image_url IS 'URL of the main representative image for the point object';

ALTER TABLE geodata.project_multilines ADD COLUMN image_url VARCHAR(1000);
COMMENT ON COLUMN geodata.project_multilines.image_url IS 'URL of the main representative image for the multiline object';

ALTER TABLE geodata.project_polygons ADD COLUMN image_url VARCHAR(1000);
COMMENT ON COLUMN geodata.project_polygons.image_url IS 'URL of the main representative image for the polygon object';
