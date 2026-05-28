--liquibase formatted sql

--changeset kutman:add-cog-to-terrain-1
ALTER TABLE geoabstraction.terrain_layers ADD COLUMN cog_object_key VARCHAR(512);
