--liquibase formatted sql

--changeset CLI:staging-rpc-init-1 splitStatements:false
CREATE OR REPLACE FUNCTION geodata.get_staging_layer(z integer, x integer, y integer, task_uuid text)
RETURNS bytea AS $$
DECLARE
    mvt bytea;
BEGIN
    SELECT ST_AsMVT(mvt_geom, 'staging_layer') INTO mvt FROM (
        SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), ST_TileEnvelope(z, x, y), extent => 4096, buffer => 64) AS mvt_geom, t.properties
        FROM geodata.temp_analysis_geometries t
        WHERE t.task_id = task_uuid::uuid 
          AND t.geom && ST_Transform(ST_TileEnvelope(z, x, y), 4326)
    ) AS mvt_geom;
    RETURN mvt;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;
