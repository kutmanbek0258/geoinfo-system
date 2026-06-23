--liquibase formatted sql

--changeset kutman:create_raster_styles_table
CREATE TABLE geoabstraction.raster_styles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'ramp',
    config JSONB NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_date TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE
);

--changeset kutman:seed_basic_raster_styles
INSERT INTO geoabstraction.raster_styles (id, name, title, type, config, is_system) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'raster', 'Default Raster Style', 'ramp', '[]'::jsonb, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'vegetation_index', 'Vegetation Index (NDVI/SAVI/EVI) Ramp', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#0000FF", "quantity": -1.0, "opacity": 1.0, "label": "Вода / Снег"}, {"color": "#E29B63", "quantity": 0.0, "opacity": 1.0, "label": "Голая почва / Застройка"}, {"color": "#FCEB92", "quantity": 0.2, "opacity": 1.0, "label": "Разреженная вегетация"}, {"color": "#A3DB6D", "quantity": 0.4, "opacity": 1.0, "label": "Умеренная растительность"}, {"color": "#328846", "quantity": 0.6, "opacity": 1.0, "label": "Здоровая растительность"}, {"color": "#0A4318", "quantity": 1.0, "opacity": 1.0, "label": "Плотный здоровый лес"}]'::jsonb, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'ndwi_water', 'NDWI Open Water Mask', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#FFFFFF", "quantity": -1.0, "opacity": 0.0, "label": "Суша (Игнорировать)"}, {"color": "#FFFFFF", "quantity": 0.0, "opacity": 0.0, "label": "Граница суши"}, {"color": "#7AC5CD", "quantity": 0.1, "opacity": 1.0, "label": "Переувлажненные зоны / Болота"}, {"color": "#4A90E2", "quantity": 0.3, "opacity": 1.0, "label": "Мелководье / Мелкие водоемы"}, {"color": "#0A2240", "quantity": 1.0, "opacity": 1.0, "label": "Глубокая открытая вода"}]'::jsonb, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'gndvi', 'Green Normalized Difference Vegetation Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#A50026", "quantity": -1.0, "opacity": 1.0, "label": "Неактивная растительность"}, {"color": "#D73027", "quantity": -0.2, "opacity": 1.0, "label": "Очень низкая активность"}, {"color": "#F46D43", "quantity": 0.0, "opacity": 1.0, "label": "Низкая активность"}, {"color": "#FDAE61", "quantity": 0.2, "opacity": 1.0, "label": "Умеренно-низкая активность"}, {"color": "#FEE08B", "quantity": 0.4, "opacity": 1.0, "label": "Умеренная активность"}, {"color": "#D9EF8B", "quantity": 0.6, "opacity": 1.0, "label": "Умеренно-высокая активность"}, {"color": "#66BD63", "quantity": 0.8, "opacity": 1.0, "label": "Высокая активность"}, {"color": "#1A9850", "quantity": 1.0, "opacity": 1.0, "label": "Очень высокая активность"}]'::jsonb, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'nbr_burn', 'Normalized Burn Ratio (NBR)', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#7A0000", "quantity": -1.0, "opacity": 1.0, "label": "Высокая степень выгорания"}, {"color": "#D73027", "quantity": -0.5, "opacity": 1.0, "label": "Умеренно-высокая степень"}, {"color": "#F46D43", "quantity": -0.2, "opacity": 1.0, "label": "Умеренно-низкая степень"}, {"color": "#FDAE61", "quantity": 0.0, "opacity": 1.0, "label": "Низкая степень"}, {"color": "#D9EF8B", "quantity": 0.1, "opacity": 1.0, "label": "Не горевшая зона"}, {"color": "#66BD63", "quantity": 0.5, "opacity": 1.0, "label": "Восстановление растительности"}, {"color": "#1A9850", "quantity": 1.0, "opacity": 1.0, "label": "Высокая плотность растительности"}]'::jsonb, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16', 'ndbi_urban', 'NDBI Urban/Built-up Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#0000FF", "quantity": -1.0, "opacity": 1.0, "label": "Вода"}, {"color": "#00FF00", "quantity": -0.1, "opacity": 1.0, "label": "Растительность"}, {"color": "#E29B63", "quantity": 0.0, "opacity": 1.0, "label": "Открытая почва"}, {"color": "#FF0000", "quantity": 0.1, "opacity": 1.0, "label": "Низкая плотность застройки"}, {"color": "#A00000", "quantity": 0.4, "opacity": 1.0, "label": "Плотная застройка / Бетон"}]'::jsonb, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a17', 'ndmi_moisture', 'NDMI Moisture Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#8B0000", "quantity": -1.0, "opacity": 1.0, "label": "Очень сухая почва / Сухостой"}, {"color": "#CD5C5C", "quantity": -0.2, "opacity": 1.0, "label": "Сухо / Дефицит влаги"}, {"color": "#F4A460", "quantity": 0.0, "opacity": 1.0, "label": "Умеренная сухость"}, {"color": "#E6E6FA", "quantity": 0.1, "opacity": 1.0, "label": "Нормальное увлажнение"}, {"color": "#4682B4", "quantity": 0.3, "opacity": 1.0, "label": "Высокое увлажнение"}, {"color": "#000080", "quantity": 1.0, "opacity": 1.0, "label": "Избыточное увлажнение / Вода"}]'::jsonb, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a18', 'ndsi_snow', 'NDSI Snow/Ice Index', 'ramp', '[{"color": "#000000", "quantity": -9999.0, "opacity": 0.0, "label": "NoData"}, {"color": "#000000", "quantity": -1.0, "opacity": 0.0, "label": "Без снега"}, {"color": "#000000", "quantity": 0.1, "opacity": 0.0, "label": "Граница снежного покрова"}, {"color": "#AFEEEE", "quantity": 0.4, "opacity": 1.0, "label": "Снежный покров / Лед"}, {"color": "#00FFFF", "quantity": 1.0, "opacity": 1.0, "label": "Плотный свежий снег"}]'::jsonb, TRUE);

--changeset kutman:alter_imagery_layers_table
-- 1. Drop the unique index for GeoServer workspace & layer name
ALTER TABLE geoabstraction.imagery_layers DROP CONSTRAINT IF EXISTS ux_imagery_ws_name;
DROP INDEX IF EXISTS geoabstraction.ux_imagery_ws_name;

-- 2. Add style_id column
ALTER TABLE geoabstraction.imagery_layers ADD COLUMN style_id UUID REFERENCES geoabstraction.raster_styles(id);

-- 3. Migrate existing styles from string 'style' column to UUID 'style_id'
UPDATE geoabstraction.imagery_layers il
SET style_id = rs.id
FROM geoabstraction.raster_styles rs
WHERE il.style = rs.name;

-- 4. Set default style for any layers that don't have a valid style matched
UPDATE geoabstraction.imagery_layers
SET style_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
WHERE style_id IS NULL;

-- 5. Drop obsolete geoserver columns and the old style column
ALTER TABLE geoabstraction.imagery_layers DROP COLUMN IF EXISTS service_url;
ALTER TABLE geoabstraction.imagery_layers DROP COLUMN IF EXISTS workspace;
ALTER TABLE geoabstraction.imagery_layers DROP COLUMN IF EXISTS style;

-- 6. Modify layer_name to be nullable (optional, but clean as we rely on cog_object_key now)
ALTER TABLE geoabstraction.imagery_layers ALTER COLUMN layer_name DROP NOT NULL;
