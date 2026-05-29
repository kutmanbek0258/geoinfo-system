-- liquibase formatted sql
-- changeset admin:add_cog_object_key_to_imagery_layers
ALTER TABLE geoabstraction.imagery_layers ADD COLUMN cog_object_key VARCHAR(512);
