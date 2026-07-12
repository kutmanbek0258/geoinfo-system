--liquibase formatted sql

--changeset admin:create_layers_table
CREATE TABLE geodata.layers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID NOT NULL REFERENCES geodata.projects(id) ON DELETE CASCADE,
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    type               VARCHAR(50) NOT NULL,
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_layers_project ON geodata.layers(project_id);

--changeset admin:migrate_folders_to_layers
-- 1. Create a default VECTOR layer for every existing project
INSERT INTO geodata.layers (id, project_id, name, description, type, characteristics, created_by, created_date)
SELECT 
    gen_random_uuid(), 
    id, 
    'Векторные данные', 
    'Основной векторный слой проекта', 
    'VECTOR', 
    '{"visible": true}'::jsonb, 
    'system', 
    NOW()
FROM geodata.projects;

-- 2. Add layer_id to folders table
ALTER TABLE geodata.folders ADD COLUMN layer_id UUID;

-- 3. Associate existing folders with the default VECTOR layers of their projects
UPDATE geodata.folders f
SET layer_id = l.id
FROM geodata.layers l
WHERE f.project_id = l.project_id AND l.type = 'VECTOR';

-- 4. Set layer_id to NOT NULL and add foreign key constraint
ALTER TABLE geodata.folders ALTER COLUMN layer_id SET NOT NULL;
ALTER TABLE geodata.folders ADD CONSTRAINT fk_folders_layers FOREIGN KEY (layer_id) REFERENCES geodata.layers(id) ON DELETE CASCADE;
CREATE INDEX ix_folders_layer ON geodata.folders(layer_id);

-- 5. Drop old project_id column and index on folders
DROP INDEX IF EXISTS geodata.ix_folders_project;
ALTER TABLE geodata.folders DROP COLUMN project_id;

--changeset admin:add_layer_id_to_geometries
-- Add layer_id columns to geometries
ALTER TABLE geodata.project_points ADD COLUMN layer_id UUID REFERENCES geodata.layers(id) ON DELETE SET NULL;
CREATE INDEX ix_pp_layer ON geodata.project_points(layer_id);

ALTER TABLE geodata.project_multilines ADD COLUMN layer_id UUID REFERENCES geodata.layers(id) ON DELETE SET NULL;
CREATE INDEX ix_ml_layer ON geodata.project_multilines(layer_id);

ALTER TABLE geodata.project_polygons ADD COLUMN layer_id UUID REFERENCES geodata.layers(id) ON DELETE SET NULL;
CREATE INDEX ix_pg_layer ON geodata.project_polygons(layer_id);

-- Map existing geometries directly to the new layer if they were in a folder
UPDATE geodata.project_points p
SET layer_id = f.layer_id
FROM geodata.folders f
WHERE p.folder_id = f.id;

UPDATE geodata.project_multilines m
SET layer_id = f.layer_id
FROM geodata.folders f
WHERE m.folder_id = f.id;

UPDATE geodata.project_polygons pg
SET layer_id = f.layer_id
FROM geodata.folders f
WHERE pg.folder_id = f.id;

--changeset admin:create_project_rasters_table
CREATE TABLE geodata.project_rasters (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id           UUID NOT NULL REFERENCES geodata.layers(id) ON DELETE CASCADE,
    folder_id          UUID REFERENCES geodata.folders(id) ON DELETE SET NULL,
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    cog_object_key     VARCHAR(512),
    bbox               GEOMETRY(MultiPolygon, 4326),
    crs                VARCHAR(32),
    colormap_id        VARCHAR(100),
    resampling         VARCHAR(50),
    date_captured      TIMESTAMP,
    status             VARCHAR(50) NOT NULL,
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_proj_raster_layer ON geodata.project_rasters(layer_id);
CREATE INDEX ix_proj_raster_folder ON geodata.project_rasters(folder_id);

--changeset admin:create_raster_layers_table
CREATE TABLE geodata.raster_layers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    cog_object_key     VARCHAR(512),
    bbox               GEOMETRY(MultiPolygon, 4326),
    crs                VARCHAR(32),
    colormap_id        VARCHAR(100),
    resampling         VARCHAR(50),
    date_captured      TIMESTAMP,
    status             VARCHAR(50) NOT NULL,
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--changeset admin:create_raster_styles_table
CREATE TABLE geodata.raster_styles (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(100) UNIQUE NOT NULL,
    title              VARCHAR(256) NOT NULL,
    type               VARCHAR(50) NOT NULL DEFAULT 'ramp',
    config             JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_system          BOOLEAN NOT NULL DEFAULT FALSE,
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--changeset admin:seed_basic_raster_styles
INSERT INTO geodata.raster_styles (id, name, title, type, config, is_system, created_by) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'raster', 'Default Raster Style', 'ramp', '[]'::jsonb, TRUE, 'system'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'vegetation_index', 'Vegetation Index (NDVI/SAVI/EVI) Ramp', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#0000FF", "quantity": -1.0, "opacity": 1.0, "label": "Вода / Снег"}, {"color": "#E29B63", "quantity": 0.0, "opacity": 1.0, "label": "Голая почва / Застройка"}, {"color": "#FCEB92", "quantity": 0.2, "opacity": 1.0, "label": "Разреженная вегетация"}, {"color": "#A3DB6D", "quantity": 0.4, "opacity": 1.0, "label": "Умеренная растительность"}, {"color": "#328846", "quantity": 0.6, "opacity": 1.0, "label": "Здоровая растительность"}, {"color": "#0A4318", "quantity": 1.0, "opacity": 1.0, "label": "Плотный здоровый лес"}]'::jsonb, TRUE, 'system'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'ndwi_water', 'NDWI Open Water Mask', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#FFFFFF", "quantity": -1.0, "opacity": 0.0, "label": "Суша (Игнорировать)"}, {"color": "#FFFFFF", "quantity": 0.0, "opacity": 0.0, "label": "Граница суши"}, {"color": "#7AC5CD", "quantity": 0.1, "opacity": 1.0, "label": "Переувлажненные зоны / Болота"}, {"color": "#4A90E2", "quantity": 0.3, "opacity": 1.0, "label": "Мелководье / Мелкие водоемы"}, {"color": "#0A2240", "quantity": 1.0, "opacity": 1.0, "label": "Глубокая открытая вода"}]'::jsonb, TRUE, 'system'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'gndvi', 'Green Normalized Difference Vegetation Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#A50026", "quantity": -1.0, "opacity": 1.0, "label": "Неактивная растительность"}, {"color": "#D73027", "quantity": -0.2, "opacity": 1.0, "label": "Очень низкая активность"}, {"color": "#F46D43", "quantity": 0.0, "opacity": 1.0, "label": "Низкая активность"}, {"color": "#FDAE61", "quantity": 0.2, "opacity": 1.0, "label": "Умеренно-низкая активность"}, {"color": "#FEE08B", "quantity": 0.4, "opacity": 1.0, "label": "Умеренная активность"}, {"color": "#D9EF8B", "quantity": 0.6, "opacity": 1.0, "label": "Умеренно-высокая активность"}, {"color": "#66BD63", "quantity": 0.8, "opacity": 1.0, "label": "Высокая активность"}, {"color": "#1A9850", "quantity": 1.0, "opacity": 1.0, "label": "Очень высокая активность"}]'::jsonb, TRUE, 'system'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'nbr_burn', 'Normalized Burn Ratio (NBR)', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#7A0000", "quantity": -1.0, "opacity": 1.0, "label": "Высокая степень выгорания"}, {"color": "#D73027", "quantity": -0.5, "opacity": 1.0, "label": "Умеренно-высокая степень"}, {"color": "#F46D43", "quantity": -0.2, "opacity": 1.0, "label": "Умеренно-низкая степень"}, {"color": "#FDAE61", "quantity": 0.0, "opacity": 1.0, "label": "Низкая степень"}, {"color": "#D9EF8B", "quantity": 0.1, "opacity": 1.0, "label": "Не горевшая зона"}, {"color": "#66BD63", "quantity": 0.5, "opacity": 1.0, "label": "Восстановление растительности"}, {"color": "#1A9850", "quantity": 1.0, "opacity": 1.0, "label": "Высокая плотность растительности"}]'::jsonb, TRUE, 'system'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16', 'ndbi_urban', 'NDBI Urban/Built-up Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#0000FF", "quantity": -1.0, "opacity": 1.0, "label": "Вода"}, {"color": "#00FF00", "quantity": -0.1, "opacity": 1.0, "label": "Растительность"}, {"color": "#E29B63", "quantity": 0.0, "opacity": 1.0, "label": "Открытая почва"}, {"color": "#FF0000", "quantity": 0.1, "opacity": 1.0, "label": "Низкая плотность застройки"}, {"color": "#A00000", "quantity": 0.4, "opacity": 1.0, "label": "Плотная застройка / Бетон"}]'::jsonb, TRUE, 'system'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a17', 'ndmi_moisture', 'NDMI Moisture Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#8B0000", "quantity": -1.0, "opacity": 1.0, "label": "Очень сухая почва / Сухостой"}, {"color": "#CD5C5C", "quantity": -0.2, "opacity": 1.0, "label": "Сухо / Дефицит влаги"}, {"color": "#F4A460", "quantity": 0.0, "opacity": 1.0, "label": "Умеренная сухость"}, {"color": "#E6E6FA", "quantity": 0.1, "opacity": 1.0, "label": "Нормальное увлажнение"}, {"color": "#4682B4", "quantity": 0.3, "opacity": 1.0, "label": "Высокое увлажнение"}, {"color": "#000080", "quantity": 1.0, "opacity": 1.0, "label": "Избыточное увлажнение / Вода"}]'::jsonb, TRUE, 'system'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a18', 'ndsi_snow', 'NDSI Snow/Ice Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#000000", "quantity": -1.0, "opacity": 0.0, "label": "Без снега"}, {"color": "#000000", "quantity": 0.1, "opacity": 0.0, "label": "Граница снежного покрова"}, {"color": "#AFEEEE", "quantity": 0.4, "opacity": 1.0, "label": "Снежный покров / Лед"}, {"color": "#00FFFF", "quantity": 1.0, "opacity": 1.0, "label": "Плотный свежий снег"}]'::jsonb, TRUE, 'system');
