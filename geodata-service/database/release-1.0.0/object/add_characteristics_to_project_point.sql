--liquibase formatted sql

--changeset your_name:add-characteristics-to-project-points
ALTER TABLE geodata.project_points
    ADD COLUMN characteristics JSONB DEFAULT '{}';

--comment: Add JSONB characteristics column to project_points table for flexible attributes.