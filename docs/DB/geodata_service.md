## **ФИНАЛЬНАЯ СХЕМА БД POSTGIS (Geo Data Service)**

-- =====================================================================  
-- Система интерактивной карты — конечная БД (PostgreSQL + PostGIS)  
-- Версия: v6-final (Actual Liquibase Sync)
-- Требования: PostgreSQL 13+ (рекоменд.), расширения: postgis, pgcrypto  
-- =====================================================================  

```sql
CREATE SCHEMA IF NOT EXISTS geodata;  
SET search_path = geodata, public;
```
-- -------------------- PROJECTS (Корневая сущность) --------------------  
```sql
CREATE TABLE IF NOT EXISTS projects (  
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    name VARCHAR(256) NOT NULL,  
    description TEXT,  
    start_date TIMESTAMP,  
    end_date TIMESTAMP,  
    created_by VARCHAR(255),  
    created_date TIMESTAMP,  
    last_modified_by VARCHAR(255),  
    last_modified_date TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix_projects_name ON projects(name); ``` 

-- -------------------- PROJECT_ACCESS (Права доступа к проектам) --------------------  
```sql
CREATE TABLE IF NOT EXISTS project_access (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_email VARCHAR(255) NOT NULL,
    permission_level VARCHAR(50) NOT NULL,
    PRIMARY KEY (project_id, user_email)
);```

-- -------------------- FOLDERS (Иерархическая структура) --------------------  
```sql
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
);```

-- -------------------- POINTS (Точечные объекты) --------------------  
```sql 
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
CREATE INDEX IF NOT EXISTS ix_pp_geom ON project_points USING GIST (geom); ``` 

-- -------------------- MULTILINES (Линейные объекты) --------------------  
```sql 
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
CREATE INDEX IF NOT EXISTS ix_ml_geom ON project_multilines USING GIST (geom);  
```
-- -------------------- POLYGONS (Полигональные объекты) --------------------  
```sql
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
CREATE INDEX IF NOT EXISTS ix_pg_geom ON project_polygons USING GIST (geom);
```

-- -------------------- IMAGERY LAYERS (Реестр слоёв GeoServer) --------------------  
-- Примечание: Актуальное управление слоями перенесено в GeoAbstraction Service.
-- Данная таблица сохранена для обратной совместимости.
```sql
CREATE TABLE IF NOT EXISTS imagery_layers (  
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    name VARCHAR(256),  
    description TEXT,  
    workspace VARCHAR(128) NOT NULL,  
    layer_name VARCHAR(256) NOT NULL,  
    service_url TEXT NOT NULL,  
    style VARCHAR(128),  
    date_captured TIMESTAMP NOT NULL,  
    crs VARCHAR(32) NOT NULL DEFAULT 'EPSG:3857',  
    status VARCHAR(16),  
    created_by VARCHAR(255),  
    created_date TIMESTAMP,  
    last_modified_by VARCHAR(255),  
    last_modified_date TIMESTAMP,  
    CONSTRAINT ux_imagery_ws_name UNIQUE (workspace, layer_name)  
);```

SET search_path = public;
