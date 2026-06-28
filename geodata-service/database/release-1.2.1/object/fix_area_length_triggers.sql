--liquibase formatted sql

--changeset admin:fix_area_length_calculation_triggers splitStatements:false
CREATE OR REPLACE FUNCTION geodata.compute_multiline_length() RETURNS trigger AS $$
BEGIN
    BEGIN
        NEW.length_m := ST_Length(NEW.geom::geography);
    EXCEPTION
        WHEN OTHERS THEN
            NEW.length_m := 0.0;
    END;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION geodata.compute_polygon_area() RETURNS trigger AS $$
BEGIN
    BEGIN
        NEW.area_m2 := ST_Area(NEW.geom::geography);
    EXCEPTION
        WHEN OTHERS THEN
            NEW.area_m2 := 0.0;
    END;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
