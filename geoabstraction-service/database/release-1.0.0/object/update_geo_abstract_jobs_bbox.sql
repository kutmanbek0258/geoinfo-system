--liquibase formatted sql

--changeset kutman:geo-abstract-job-bbox-to-multipolygon
-- Change geometry type from Polygon to MultiPolygon to support COG extents
ALTER TABLE geoabstraction.geo_abstract_jobs 
    ALTER COLUMN bbox TYPE GEOMETRY(MultiPolygon, 4326) 
    USING ST_Multi(bbox);
