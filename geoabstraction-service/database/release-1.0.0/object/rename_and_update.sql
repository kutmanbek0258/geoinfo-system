--liquibase formatted sql

--changeset kutman:geoabstraction-refactor-1
ALTER SCHEMA terrain RENAME TO geoabstraction;

ALTER TABLE geoabstraction.terrain_jobs RENAME TO geo_abstract_jobs;

ALTER TABLE geoabstraction.geo_abstract_jobs ADD COLUMN task_type VARCHAR(50);
ALTER TABLE geoabstraction.geo_abstract_jobs ADD COLUMN characteristics JSONB;

UPDATE geoabstraction.geo_abstract_jobs SET task_type = 'TERRAIN_MESH';
