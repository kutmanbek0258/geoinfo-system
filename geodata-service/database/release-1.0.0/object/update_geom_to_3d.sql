--liquibase formatted sql

--changeSet geoinfo:update-geom-to-3d-01
-- Изменение типов колонок для поддержки Z-измерения (высоты)
ALTER TABLE geodata.project_points 
    ALTER COLUMN geom TYPE geometry(PointZ, 4326) USING ST_Force3DZ(geom);

ALTER TABLE geodata.project_multilines 
    ALTER COLUMN geom TYPE geometry(MultiLineStringZ, 4326) USING ST_Force3DZ(geom);

ALTER TABLE geodata.project_polygons 
    ALTER COLUMN geom TYPE geometry(PolygonZ, 4326) USING ST_Force3DZ(geom);
