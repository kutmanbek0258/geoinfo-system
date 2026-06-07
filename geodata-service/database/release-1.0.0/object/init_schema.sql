-- Consolidated Initial Schema for GeoInfoSystem (PostGIS)
-- Includes all features from release 1.0.0 and 1.1.0

CREATE SCHEMA IF NOT EXISTS geodata;

-- Extensions
CREATE EXTENSION IF NOT EXISTS plpgsql;
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

SET search_path = geodata, public;

-- -------------------- PROJECTS --------------------
CREATE TABLE IF NOT EXISTS geodata.projects (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    start_date         TIMESTAMP,
    end_date           TIMESTAMP,
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_projects_name ON geodata.projects(name);

-- -------------------- FOLDERS --------------------
CREATE TABLE IF NOT EXISTS geodata.folders (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID NOT NULL REFERENCES geodata.projects(id) ON DELETE CASCADE,
    parent_id          UUID REFERENCES geodata.folders(id) ON DELETE SET NULL,
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_folders_project ON geodata.folders(project_id);
CREATE INDEX IF NOT EXISTS ix_folders_parent ON geodata.folders(parent_id);

-- -------------------- POINTS --------------------
CREATE TABLE IF NOT EXISTS geodata.project_points (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID NOT NULL REFERENCES geodata.projects(id) ON DELETE CASCADE,
    folder_id          UUID REFERENCES geodata.folders(id) ON DELETE SET NULL,
    name               VARCHAR(256),
    description        TEXT,
    status             VARCHAR(16),
    geom               GEOMETRY(MultiPointZ, 4326) NOT NULL,
    image_url          VARCHAR(1000),
    characteristics    JSONB DEFAULT '{}',
    bbox               GEOMETRY(Polygon, 4326),
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_pp_project ON geodata.project_points(project_id);
CREATE INDEX IF NOT EXISTS ix_pp_folder  ON geodata.project_points(folder_id);
CREATE INDEX IF NOT EXISTS ix_pp_status  ON geodata.project_points(status);
CREATE INDEX IF NOT EXISTS ix_pp_geom    ON geodata.project_points USING GIST (geom);

-- -------------------- MULTILINES --------------------
CREATE TABLE IF NOT EXISTS geodata.project_multilines (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID NOT NULL REFERENCES geodata.projects(id) ON DELETE CASCADE,
    folder_id          UUID REFERENCES geodata.folders(id) ON DELETE SET NULL,
    name               VARCHAR(256),
    description        TEXT,
    status             VARCHAR(16),
    length_m           DOUBLE PRECISION,
    geom               GEOMETRY(MultiLineStringZ, 4326) NOT NULL,
    image_url          VARCHAR(1000),
    characteristics    JSONB DEFAULT '{}',
    bbox               GEOMETRY(Polygon, 4326),
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ml_length_nonneg CHECK (length_m IS NULL OR length_m >= 0)
);
CREATE INDEX IF NOT EXISTS ix_ml_project ON geodata.project_multilines(project_id);
CREATE INDEX IF NOT EXISTS ix_ml_folder  ON geodata.project_multilines(folder_id);
CREATE INDEX IF NOT EXISTS ix_ml_status  ON geodata.project_multilines(status);
CREATE INDEX IF NOT EXISTS ix_ml_geom    ON geodata.project_multilines USING GIST (geom);

-- -------------------- POLYGONS --------------------
CREATE TABLE IF NOT EXISTS geodata.project_polygons (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID NOT NULL REFERENCES geodata.projects(id) ON DELETE CASCADE,
    folder_id          UUID REFERENCES geodata.folders(id) ON DELETE SET NULL,
    name               VARCHAR(256),
    description        TEXT,
    status             VARCHAR(16),
    area_m2            DOUBLE PRECISION,
    geom               GEOMETRY(MultiPolygonZ, 4326) NOT NULL,
    image_url          VARCHAR(1000),
    characteristics    JSONB DEFAULT '{}',
    bbox               GEOMETRY(Polygon, 4326),
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pg_area_nonneg CHECK (area_m2 IS NULL OR area_m2 >= 0)
);
CREATE INDEX IF NOT EXISTS ix_pg_project ON geodata.project_polygons(project_id);
CREATE INDEX IF NOT EXISTS ix_pg_folder  ON geodata.project_polygons(folder_id);
CREATE INDEX IF NOT EXISTS ix_pg_status  ON geodata.project_polygons(status);
CREATE INDEX IF NOT EXISTS ix_pg_geom    ON geodata.project_polygons USING GIST (geom);

-- -------------------- PROJECT ACCESS --------------------
CREATE TABLE IF NOT EXISTS geodata.project_access (
    project_id       UUID NOT NULL REFERENCES geodata.projects(id) ON DELETE CASCADE,
    user_email       VARCHAR(255) NOT NULL,
    permission_level VARCHAR(50) NOT NULL,
    PRIMARY KEY (project_id, user_email)
);

-- =====================================================================
--  FUNCTIONS & TRIGGERS
-- =====================================================================

-- Auto-calculate Length
CREATE OR REPLACE FUNCTION geodata.compute_multiline_length() RETURNS trigger AS $$
BEGIN
    NEW.length_m := ST_Length(NEW.geom::geography);
    RETURN NEW;
END; $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ml_len_biur ON geodata.project_multilines;
CREATE TRIGGER trg_ml_len_biur BEFORE INSERT OR UPDATE ON geodata.project_multilines
FOR EACH ROW EXECUTE FUNCTION geodata.compute_multiline_length();

-- Auto-calculate Area
CREATE OR REPLACE FUNCTION geodata.compute_polygon_area() RETURNS trigger AS $$
BEGIN
    NEW.area_m2 := ST_Area(NEW.geom::geography);
    RETURN NEW;
END; $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_pg_area_biur ON geodata.project_polygons;
CREATE TRIGGER trg_pg_area_biur BEFORE INSERT OR UPDATE ON geodata.project_polygons
FOR EACH ROW EXECUTE FUNCTION geodata.compute_polygon_area();

-- Auto-update BBOX
CREATE OR REPLACE FUNCTION geodata.update_geo_object_bbox() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.geom IS NOT NULL THEN
        NEW.bbox := ST_SetSRID(ST_Envelope(NEW.geom), 4326);
    ELSE
        NEW.bbox := NULL;
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_project_points_bbox ON geodata.project_points;
CREATE TRIGGER trg_update_project_points_bbox BEFORE INSERT OR UPDATE OF geom ON geodata.project_points
FOR EACH ROW EXECUTE FUNCTION geodata.update_geo_object_bbox();

DROP TRIGGER IF EXISTS trg_update_project_multilines_bbox ON geodata.project_multilines;
CREATE TRIGGER trg_update_project_multilines_bbox BEFORE INSERT OR UPDATE OF geom ON geodata.project_multilines
FOR EACH ROW EXECUTE FUNCTION geodata.update_geo_object_bbox();

DROP TRIGGER IF EXISTS trg_update_project_polygons_bbox ON geodata.project_polygons;
CREATE TRIGGER trg_update_project_polygons_bbox BEFORE INSERT OR UPDATE OF geom ON geodata.project_polygons
FOR EACH ROW EXECUTE FUNCTION geodata.update_geo_object_bbox();

-- =====================================================================
--  MVT VIEWS & FUNCTIONS
-- =====================================================================

CREATE OR REPLACE VIEW geodata.v_project_points_mvt AS
SELECT id, project_id, folder_id, name, status, characteristics, geom FROM geodata.project_points WHERE geom IS NOT NULL;

CREATE OR REPLACE VIEW geodata.v_project_multilines_mvt AS
SELECT id, project_id, folder_id, name, status, characteristics, geom FROM geodata.project_multilines WHERE geom IS NOT NULL;

CREATE OR REPLACE VIEW geodata.v_project_polygons_mvt AS
SELECT id, project_id, folder_id, name, status, characteristics, geom FROM geodata.project_polygons WHERE geom IS NOT NULL;

CREATE OR REPLACE FUNCTION geodata.mvt_project_points(z integer, x integer, y integer, project_id_param uuid) RETURNS bytea AS $$
DECLARE res bytea;
BEGIN
    WITH bounds AS (SELECT ST_TileEnvelope(z, x, y) AS geom),
    mvtgeom AS (
      SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), bounds.geom) AS geom,
             t.id, t.name, t.status, t.characteristics::text AS characteristics
      FROM geodata.project_points t, bounds
      WHERE t.project_id = project_id_param AND ST_Intersects(ST_Transform(t.geom, 3857), bounds.geom)
    ) SELECT ST_AsMVT(mvtgeom, 'geodata.mvt_project_points') INTO res FROM mvtgeom;
    RETURN res;
END; $$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;

CREATE OR REPLACE FUNCTION geodata.mvt_project_multilines(z integer, x integer, y integer, project_id_param uuid) RETURNS bytea AS $$
DECLARE res bytea;
BEGIN
    WITH bounds AS (SELECT ST_TileEnvelope(z, x, y) AS geom),
    mvtgeom AS (
      SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), bounds.geom) AS geom,
             t.id, t.name, t.status, t.characteristics::text AS characteristics
      FROM geodata.project_multilines t, bounds
      WHERE t.project_id = project_id_param AND ST_Intersects(ST_Transform(t.geom, 3857), bounds.geom)
    ) SELECT ST_AsMVT(mvtgeom, 'geodata.mvt_project_multilines') INTO res FROM mvtgeom;
    RETURN res;
END; $$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;

CREATE OR REPLACE FUNCTION geodata.mvt_project_polygons(z integer, x integer, y integer, project_id_param uuid) RETURNS bytea AS $$
DECLARE res bytea;
BEGIN
    WITH bounds AS (SELECT ST_TileEnvelope(z, x, y) AS geom),
    mvtgeom AS (
      SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), bounds.geom) AS geom,
             t.id, t.name, t.status, t.characteristics::text AS characteristics
      FROM geodata.project_polygons t, bounds
      WHERE t.project_id = project_id_param AND ST_Intersects(ST_Transform(t.geom, 3857), bounds.geom)
    ) SELECT ST_AsMVT(mvtgeom, 'geodata.mvt_project_polygons') INTO res FROM mvtgeom;
    RETURN res;
END; $$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;
