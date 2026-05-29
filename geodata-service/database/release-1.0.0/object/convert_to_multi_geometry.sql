-- liquibase formatted sql
-- changeset admin:convert_to_multi_geometry
-- Convert Points to MultiPoint
ALTER TABLE geodata.project_points 
    ALTER COLUMN geom TYPE geometry(MultiPointZ, 4326) 
    USING ST_Multi(geom);

-- Convert Polygons to MultiPolygon
ALTER TABLE geodata.project_polygons 
    ALTER COLUMN geom TYPE geometry(MultiPolygonZ, 4326) 
    USING ST_Multi(geom);
