--liquibase formatted sql

--changeset CLI:staging-rpc-init-1 splitStatements:false
CREATE OR REPLACE FUNCTION geodata.get_staging_layer(z integer, x integer, y integer, task_uuid uuid)
    RETURNS bytea AS $$
DECLARE
    bounds geometry;
    res bytea;
BEGIN
    -- Генерируем границу тайла
    bounds := ST_TileEnvelope(z, x, y);

    WITH mvt_data AS (
        SELECT
            t.id,
            t.properties,
            -- ST_Force2D удаляет Z-координату и решает проблему ошибки 2/0
            ST_AsMVTGeom(ST_Transform(ST_Force2D(t.geom), 3857), bounds) AS geom
        FROM geodata.temp_analysis_geometries t
        WHERE t.task_id = task_uuid
          -- Оптимизация: трансформируем bounds, чтобы работал пространственный индекс по t.geom
          AND ST_Intersects(t.geom, ST_Transform(bounds, 4326))
    )
    SELECT ST_AsMVT(mvt_data.*, 'geodata.staging_layer') INTO res FROM mvt_data;

    RETURN res;
END; $$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;