--liquibase formatted sql

--changeset admin:create_mvt_functions splitStatements:false
-- Function for Project Points MVT
CREATE OR REPLACE FUNCTION geodata.mvt_project_points(z integer, x integer, y integer, project_id_param uuid)
RETURNS bytea
AS $$
DECLARE
    res bytea;
BEGIN
    WITH
    bounds AS (
      SELECT ST_TileEnvelope(z, x, y) AS geom
    ),
    mvtgeom AS (
      SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), bounds.geom) AS geom,
             t.id, t.name, t.status, t.characteristics
      FROM geodata.project_points t, bounds
      WHERE t.project_id = project_id_param
        AND ST_Intersects(ST_Transform(t.geom, 3857), bounds.geom)
    )
    SELECT ST_AsMVT(mvtgeom, 'geodata.mvt_project_points')
    INTO res
    FROM mvtgeom;

    RETURN res;
END;
$$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;

-- Function for Project Multilines MVT
CREATE OR REPLACE FUNCTION geodata.mvt_project_multilines(z integer, x integer, y integer, project_id_param uuid)
RETURNS bytea
AS $$
DECLARE
    res bytea;
BEGIN
    WITH
    bounds AS (
      SELECT ST_TileEnvelope(z, x, y) AS geom
    ),
    mvtgeom AS (
      SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), bounds.geom) AS geom,
             t.id, t.name, t.status, t.characteristics
      FROM geodata.project_multilines t, bounds
      WHERE t.project_id = project_id_param
        AND ST_Intersects(ST_Transform(t.geom, 3857), bounds.geom)
    )
    SELECT ST_AsMVT(mvtgeom, 'geodata.mvt_project_multilines')
    INTO res
    FROM mvtgeom;

    RETURN res;
END;
$$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;

-- Function for Project Polygons MVT
CREATE OR REPLACE FUNCTION geodata.mvt_project_polygons(z integer, x integer, y integer, project_id_param uuid)
RETURNS bytea
AS $$
DECLARE
    res bytea;
BEGIN
    WITH
    bounds AS (
      SELECT ST_TileEnvelope(z, x, y) AS geom
    ),
    mvtgeom AS (
      SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), bounds.geom) AS geom,
             t.id, t.name, t.status, t.characteristics
      FROM geodata.project_polygons t, bounds
      WHERE t.project_id = project_id_param
        AND ST_Intersects(ST_Transform(t.geom, 3857), bounds.geom)
    )
    SELECT ST_AsMVT(mvtgeom, 'geodata.mvt_project_polygons')
    INTO res
    FROM mvtgeom;

    RETURN res;
END;
$$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;
