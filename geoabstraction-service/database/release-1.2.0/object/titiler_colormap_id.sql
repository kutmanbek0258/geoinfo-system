--liquibase formatted sql

--changeset kutman:add_colormap_id_and_resampling_to_imagery_layers
ALTER TABLE geoabstraction.imagery_layers ADD COLUMN colormap_id VARCHAR(100);
ALTER TABLE geoabstraction.imagery_layers ADD COLUMN resampling VARCHAR(50);
