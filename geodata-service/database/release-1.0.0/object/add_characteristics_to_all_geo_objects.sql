--liquibase formatted sql

--changeset your_name:add-characteristics-to-multilines-and-polygons
ALTER TABLE geodata.project_multilines
    ADD COLUMN characteristics JSONB DEFAULT '{}';

ALTER TABLE geodata.project_polygons
    ADD COLUMN characteristics JSONB DEFAULT '{}';

--comment: Add JSONB characteristics column to project_multilines and project_polygons tables for flexible attributes and styling.
