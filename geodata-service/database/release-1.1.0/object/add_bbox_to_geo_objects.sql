-- Add bbox column to project_points
ALTER TABLE geodata.project_points ADD COLUMN IF NOT EXISTS bbox geometry(Polygon, 4326);
-- Add bbox column to project_multilines
ALTER TABLE geodata.project_multilines ADD COLUMN IF NOT EXISTS bbox geometry(Polygon, 4326);
-- Add bbox column to project_polygons
ALTER TABLE geodata.project_polygons ADD COLUMN IF NOT EXISTS bbox geometry(Polygon, 4326);

-- Create trigger function to update bbox
CREATE OR REPLACE FUNCTION geodata.update_geo_object_bbox()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.geom IS NOT NULL THEN
        NEW.bbox := ST_SetSRID(ST_Envelope(NEW.geom), 4326);
    ELSE
        NEW.bbox := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for project_points
DROP TRIGGER IF EXISTS trg_update_project_points_bbox ON geodata.project_points;
CREATE TRIGGER trg_update_project_points_bbox
BEFORE INSERT OR UPDATE OF geom ON geodata.project_points
FOR EACH ROW EXECUTE FUNCTION geodata.update_geo_object_bbox();

-- Triggers for project_multilines
DROP TRIGGER IF EXISTS trg_update_project_multilines_bbox ON geodata.project_multilines;
CREATE TRIGGER trg_update_project_multilines_bbox
BEFORE INSERT OR UPDATE OF geom ON geodata.project_multilines
FOR EACH ROW EXECUTE FUNCTION geodata.update_geo_object_bbox();

-- Triggers for project_polygons
DROP TRIGGER IF EXISTS trg_update_project_polygons_bbox ON geodata.project_polygons;
CREATE TRIGGER trg_update_project_polygons_bbox
BEFORE INSERT OR UPDATE OF geom ON geodata.project_polygons
FOR EACH ROW EXECUTE FUNCTION geodata.update_geo_object_bbox();

-- Populate existing bboxes
UPDATE geodata.project_points SET bbox = ST_SetSRID(ST_Envelope(geom), 4326) WHERE geom IS NOT NULL AND bbox IS NULL;
UPDATE geodata.project_multilines SET bbox = ST_SetSRID(ST_Envelope(geom), 4326) WHERE geom IS NOT NULL AND bbox IS NULL;
UPDATE geodata.project_polygons SET bbox = ST_SetSRID(ST_Envelope(geom), 4326) WHERE geom IS NOT NULL AND bbox IS NULL;
