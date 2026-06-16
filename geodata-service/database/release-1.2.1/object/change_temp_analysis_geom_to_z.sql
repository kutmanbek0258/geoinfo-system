--liquibase formatted sql
--changeset admin:change_temp_analysis_geometries_geom_to_z

ALTER TABLE geodata.temp_analysis_geometries
ALTER COLUMN geom TYPE GEOMETRY(GeometryZ, 4326) USING ST_Force3D(geom);
