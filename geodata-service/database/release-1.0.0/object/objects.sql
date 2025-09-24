-- =====================================================================
--  Система интерактивной карты — конечная БД (PostgreSQL + PostGIS)
--  Версия: v4-final
--  Требования: PostgreSQL 13+ (рекоменд.), расширения: postgis, pgcrypto
-- =====================================================================

-- ---------- БАЗОВЫЕ НАСТРОЙКИ / РАСШИРЕНИЯ ----------
CREATE SCHEMA IF NOT EXISTS geodata;

-- Язык plpgsql обычно предустановлен, но на всякий случай:
CREATE EXTENSION IF NOT EXISTS plpgsql;

-- Гео-функции:
CREATE EXTENSION IF NOT EXISTS postgis;

-- Для генерации UUID: gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

SET search_path = geodata, public;

-- =====================================================================
--  GEODATA: ТАБЛИЦЫ
-- =====================================================================

-- -------------------- PROJECTS --------------------
CREATE TABLE IF NOT EXISTS projects (
                                        id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        name         VARCHAR(256) NOT NULL,
                                        description  TEXT,
                                        start_date   DATE,
                                        end_date     DATE,
                                        created_by        VARCHAR(255),
                                        created_date      TIMESTAMP,
                                        last_modified_by  VARCHAR(255),
                                        last_modified_date TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_projects_name ON projects(name);

-- -------------------- POINTS --------------------
CREATE TABLE IF NOT EXISTS project_points (
                                              id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                              project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                                              name         VARCHAR(256),
                                              description  TEXT,
                                              status       VARCHAR(16),
                                              geom         geometry(Point, 4326) NOT NULL,
                                              image_link   VARCHAR(1000),
                                              created_by        VARCHAR(255),
                                              created_date      TIMESTAMP,
                                              last_modified_by  VARCHAR(255),
                                              last_modified_date TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_pp_project   ON project_points(project_id);
CREATE INDEX IF NOT EXISTS ix_pp_status    ON project_points(status);
CREATE INDEX IF NOT EXISTS ix_pp_geom      ON project_points USING GIST (geom);

-- -------------------- MULTILINES --------------------
CREATE TABLE IF NOT EXISTS project_multilines (
                                                  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                  project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                                                  name         VARCHAR(256),
                                                  description  TEXT,
                                                  status       VARCHAR(16),
                                                  length_m     DOUBLE PRECISION,         -- авторасчёт суммарной длины
                                                  geom         geometry(MultiLineString, 4326) NOT NULL,
                                                  created_by        VARCHAR(255),
                                                  created_date      TIMESTAMP,
                                                  last_modified_by  VARCHAR(255),
                                                  last_modified_date TIMESTAMP,
                                                  CONSTRAINT chk_ml_length_nonneg CHECK (length_m IS NULL OR length_m >= 0)
);
CREATE INDEX IF NOT EXISTS ix_ml_project   ON project_multilines(project_id);
CREATE INDEX IF NOT EXISTS ix_ml_status    ON project_multilines(status);
CREATE INDEX IF NOT EXISTS ix_ml_geom      ON project_multilines USING GIST (geom);

-- -------------------- POLYGONS --------------------
CREATE TABLE IF NOT EXISTS project_polygons (
                                                id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                                                name         VARCHAR(256),
                                                description  TEXT,
                                                status       VARCHAR(16),
                                                area_m2      DOUBLE PRECISION,         -- авторасчёт площади
                                                geom         geometry(Polygon, 4326) NOT NULL,
                                                created_by        VARCHAR(255),
                                                created_date      TIMESTAMP,
                                                last_modified_by  VARCHAR(255),
                                                last_modified_date TIMESTAMP,
                                                CONSTRAINT chk_pg_area_nonneg CHECK (area_m2 IS NULL OR area_m2 >= 0)
);
CREATE INDEX IF NOT EXISTS ix_pg_project   ON project_polygons(project_id);
CREATE INDEX IF NOT EXISTS ix_pg_status    ON project_polygons(status);
CREATE INDEX IF NOT EXISTS ix_pg_geom      ON project_polygons USING GIST (geom);

-- -------------------- IMAGERY LAYERS (Реестр слоёв GeoServer) --------------------
CREATE TABLE IF NOT EXISTS imagery_layers (
                                              id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                              name              VARCHAR(256),
                                              description       TEXT,
                                              workspace         VARCHAR(128)  NOT NULL,
                                              layer_name        VARCHAR(256)  NOT NULL,
                                              service_url       TEXT          NOT NULL,  -- базовый WMS/WMTS endpoint
                                              style             VARCHAR(128),
                                              date_captured     DATE          NOT NULL,
                                              crs               VARCHAR(32)   NOT NULL DEFAULT 'EPSG:3857',
                                              status            VARCHAR(16),
                                              created_by        VARCHAR(255),
                                              created_date      TIMESTAMP,
                                              last_modified_by  VARCHAR(255),
                                              last_modified_date TIMESTAMP,
                                              CONSTRAINT ux_imagery_ws_name UNIQUE (workspace, layer_name)
);
CREATE INDEX IF NOT EXISTS ix_imagery_date ON imagery_layers(date_captured);

-- =====================================================================
--  GEODATA: ФУНКЦИИ/ТРИГГЕРЫ
-- =====================================================================

-- Подсчёт длины MultiLine (выберите метод; по умолчанию точнее через geography)
CREATE OR REPLACE FUNCTION compute_multiline_length()
    RETURNS trigger AS $$
BEGIN
    -- Точный вариант (метры, сферическая геодезия):
    NEW.length_m := ST_Length(NEW.geom::geography);
    -- Быстрый вариант (псевдометры): раскомментируйте, если нужен performance
    -- NEW.length_m := ST_Length(ST_Transform(NEW.geom, 3857));

    RETURN NEW;
END; $$ LANGUAGE plpgsql;

-- Подсчёт площади Polygon
CREATE OR REPLACE FUNCTION compute_polygon_area()
    RETURNS trigger AS $$
BEGIN
    -- Точный вариант (метры^2, сферическая геодезия):
    NEW.area_m2 := ST_Area(NEW.geom::geography);
    -- Быстрый вариант (псевдометры^2):
    -- NEW.area_m2 := ST_Area(ST_Transform(NEW.geom, 3857));

    RETURN NEW;
END; $$ LANGUAGE plpgsql;

-- Триггеры пересчёта длины/площади
DROP TRIGGER IF EXISTS trg_ml_len_biur ON project_multilines;
CREATE TRIGGER trg_ml_len_biur
    BEFORE INSERT OR UPDATE ON project_multilines
    FOR EACH ROW EXECUTE FUNCTION compute_multiline_length();

DROP TRIGGER IF EXISTS trg_pg_area_biur ON project_polygons;
CREATE TRIGGER trg_pg_area_biur
    BEFORE INSERT OR UPDATE ON project_polygons
    FOR EACH ROW EXECUTE FUNCTION compute_polygon_area();

-- =====================================================================
--  ВОЗВРАТ search_path
-- =====================================================================
SET search_path = public;

-- ========================= КОНЕЦ СКРИПТА =============================
