--liquibase formatted sql

--changeset kutman:imagery-layer-bbox-1
ALTER TABLE geoabstraction.imagery_layers ADD COLUMN bbox GEOMETRY(MultiPolygon, 4326);

CREATE INDEX idx_imagery_layers_bbox ON geoabstraction.imagery_layers USING GIST (bbox);
