## **ФИНАЛЬНАЯ СХЕМА БД POSTGIS (Geo Data Service)**

-- =====================================================================  
-- Система интерактивной карты — конечная БД (PostgreSQL + PostGIS)  
-- Версия: v5-final (Hierarchy support)
-- Требования: PostgreSQL 13+ (рекоменд.), расширения: postgis, pgcrypto  
-- =====================================================================  

-- ---------- БАЗОВЫЕ НАСТРОЙКИ / РАСШИРЕНИЯ ----------  
CREATE SCHEMA IF NOT EXISTS geodata;  
CREATE EXTENSION IF NOT EXISTS plpgsql;  
CREATE EXTENSION IF NOT EXISTS postgis;  
CREATE EXTENSION IF NOT EXISTS pgcrypto; 
SET search_path = geodata, public;

-- =====================================================================  
-- GEODATA: ТАБЛИЦЫ  
-- =====================================================================  

-- -------------------- PROJECTS (Корневая сущность) --------------------  
CREATE TABLE IF NOT EXISTS projects (  
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    name VARCHAR(256) NOT NULL,  
    description TEXT,  
    start_date DATE,  
    end_date DATE,  
    created_by VARCHAR(255),  
    created_date TIMESTAMP,  
    last_modified_by VARCHAR(255),  
    last_modified_date TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix_projects_name ON projects(name);  

-- -------------------- FOLDERS (Иерархическая структура) --------------------  
CREATE TABLE IF NOT EXISTS folders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    parent_id    UUID REFERENCES folders(id) ON DELETE SET NULL,
    name         VARCHAR(256) NOT NULL,
    description  TEXT,
    characteristics JSONB,
    created_by        VARCHAR(255),
    created_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by  VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_folders_project ON folders(project_id);
CREATE INDEX IF NOT EXISTS ix_folders_parent ON folders(parent_id);

-- -------------------- POINTS (Точечные объекты) --------------------  
CREATE TABLE IF NOT EXISTS project_points (  
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
    folder_id UUID REFERENCES folders(id) ON DELETE SET NULL,
    name VARCHAR(256),  
    description TEXT,  
    status VARCHAR(16),  
    geom geometry(Point, 4326) NOT NULL,  
    image_url VARCHAR(1000),  
    characteristics JSONB,  
    created_by VARCHAR(255),  
    created_date TIMESTAMP,  
    last_modified_by VARCHAR(255),  
    last_modified_date TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix_pp_project ON project_points(project_id);  
CREATE INDEX IF NOT EXISTS ix_pp_folder ON project_points(folder_id);
CREATE INDEX IF NOT EXISTS ix_pp_geom ON project_points USING GIST (geom);  

-- -------------------- MULTILINES (Линейные объекты) --------------------  
CREATE TABLE IF NOT EXISTS project_multilines (  
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
    folder_id UUID REFERENCES folders(id) ON DELETE SET NULL,
    name VARCHAR(256),  
    description TEXT,  
    status VARCHAR(16),  
    length_m DOUBLE PRECISION,  
    geom geometry(MultiLineString, 4326) NOT NULL,  
    characteristics JSONB,  
    created_by VARCHAR(255),  
    created_date TIMESTAMP,  
    last_modified_by VARCHAR(255),  
    last_modified_date TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix_ml_project ON project_multilines(project_id);  
CREATE INDEX IF NOT EXISTS ix_ml_folder ON project_multilines(folder_id);
CREATE INDEX IF NOT EXISTS ix_ml_geom ON project_multilines USING GIST (geom);  

-- -------------------- POLYGONS (Полигональные объекты) --------------------  
CREATE TABLE IF NOT EXISTS project_polygons (  
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
    folder_id UUID REFERENCES folders(id) ON DELETE SET NULL,
    name VARCHAR(256),  
    description TEXT,  
    status VARCHAR(16),  
    area_m2 DOUBLE PRECISION,  
    geom geometry(Polygon, 4326) NOT NULL,  
    characteristics JSONB,  
    created_by VARCHAR(255),  
    created_date TIMESTAMP,  
    last_modified_by VARCHAR(255),  
    last_modified_date TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix_pg_project ON project_polygons(project_id);  
CREATE INDEX IF NOT EXISTS ix_pg_folder ON project_polygons(folder_id);
CREATE INDEX IF NOT EXISTS ix_pg_geom ON project_polygons USING GIST (geom);  

-- -------------------- IMAGERY LAYERS (Реестр слоёв GeoServer) --------------------  
CREATE TABLE IF NOT EXISTS imagery_layers (  
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    name VARCHAR(256),  
    description TEXT,  
    workspace VARCHAR(128) NOT NULL,  
    layer_name VARCHAR(256) NOT NULL,  
    service_url TEXT NOT NULL,  
    style VARCHAR(128),  
    date_captured DATE NOT NULL,  
    crs VARCHAR(32) NOT NULL DEFAULT 'EPSG:3857',  
    status VARCHAR(16),  
    created_by VARCHAR(255),  
    created_date TIMESTAMP,  
    last_modified_by VARCHAR(255),  
    last_modified_date TIMESTAMP,  
    CONSTRAINT ux_imagery_ws_name UNIQUE (workspace, layer_name)  
);  

-- ---------- ТРИГГЕРЫ АВТОРАСЧЁТА ----------
-- (Функции compute_multiline_length и compute_polygon_area опущены для краткости)

SET search_path = public;
