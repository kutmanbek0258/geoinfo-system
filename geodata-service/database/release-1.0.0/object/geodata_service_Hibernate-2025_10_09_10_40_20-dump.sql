--
-- Name: geodata; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA geodata;


ALTER SCHEMA geodata OWNER TO postgres;

--
-- Name: compute_multiline_length(); Type: FUNCTION; Schema: geodata; Owner: postgres
--

CREATE FUNCTION geodata.compute_multiline_length() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Точный вариант (метры, сферическая геодезия):
    NEW.length_m := ST_Length(NEW.geom::geography);
    -- Быстрый вариант (псевдометры): раскомментируйте, если нужен performance
    -- NEW.length_m := ST_Length(ST_Transform(NEW.geom, 3857));

    RETURN NEW;
END; $$;


ALTER FUNCTION geodata.compute_multiline_length() OWNER TO postgres;

--
-- Name: compute_polygon_area(); Type: FUNCTION; Schema: geodata; Owner: postgres
--

CREATE FUNCTION geodata.compute_polygon_area() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Точный вариант (метры^2, сферическая геодезия):
    NEW.area_m2 := ST_Area(NEW.geom::geography);
    -- Быстрый вариант (псевдометры^2):
    -- NEW.area_m2 := ST_Area(ST_Transform(NEW.geom, 3857));

    RETURN NEW;
END; $$;


ALTER FUNCTION geodata.compute_polygon_area() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: imagery_layers; Type: TABLE; Schema: geodata; Owner: postgres
--

CREATE TABLE geodata.imagery_layers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(256),
    description text,
    workspace character varying(128) NOT NULL,
    layer_name character varying(256) NOT NULL,
    service_url text NOT NULL,
    style character varying(128),
    date_captured timestamp(6) without time zone NOT NULL,
    crs character varying(32) DEFAULT 'EPSG:3857'::character varying NOT NULL,
    status character varying(16),
    created_by character varying(255),
    created_date timestamp without time zone,
    last_modified_by character varying(255),
    last_modified_date timestamp without time zone
);


ALTER TABLE geodata.imagery_layers OWNER TO postgres;

--
-- Name: project_multilines; Type: TABLE; Schema: geodata; Owner: postgres
--

CREATE TABLE geodata.project_multilines (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    project_id uuid NOT NULL,
    name character varying(256),
    description text,
    status character varying(16),
    length_m double precision,
    geom public.geometry(MultiLineString,4326) NOT NULL,
    created_by character varying(255),
    created_date timestamp without time zone,
    last_modified_by character varying(255),
    last_modified_date timestamp without time zone,
    CONSTRAINT chk_ml_length_nonneg CHECK (((length_m IS NULL) OR (length_m >= (0)::double precision)))
);


ALTER TABLE geodata.project_multilines OWNER TO postgres;

--
-- Name: project_points; Type: TABLE; Schema: geodata; Owner: postgres
--

CREATE TABLE geodata.project_points (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    project_id uuid NOT NULL,
    name character varying(256),
    description text,
    status character varying(16),
    geom public.geometry(Point,4326) NOT NULL,
    image_link character varying(1000),
    created_by character varying(255),
    created_date timestamp without time zone,
    last_modified_by character varying(255),
    last_modified_date timestamp without time zone
);


ALTER TABLE geodata.project_points OWNER TO postgres;

--
-- Name: project_polygons; Type: TABLE; Schema: geodata; Owner: postgres
--

CREATE TABLE geodata.project_polygons (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    project_id uuid NOT NULL,
    name character varying(256),
    description text,
    status character varying(16),
    area_m2 double precision,
    geom public.geometry(Polygon,4326) NOT NULL,
    created_by character varying(255),
    created_date timestamp without time zone,
    last_modified_by character varying(255),
    last_modified_date timestamp without time zone,
    CONSTRAINT chk_pg_area_nonneg CHECK (((area_m2 IS NULL) OR (area_m2 >= (0)::double precision)))
);


ALTER TABLE geodata.project_polygons OWNER TO postgres;

--
-- Name: projects; Type: TABLE; Schema: geodata; Owner: postgres
--

CREATE TABLE geodata.projects (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(256) NOT NULL,
    description text,
    start_date timestamp(6) without time zone,
    end_date timestamp(6) without time zone,
    created_by character varying(255),
    created_date timestamp without time zone,
    last_modified_by character varying(255),
    last_modified_date timestamp without time zone
);


ALTER TABLE geodata.projects OWNER TO postgres;

--
-- Name: imagery_layers imagery_layers_pkey; Type: CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.imagery_layers
    ADD CONSTRAINT imagery_layers_pkey PRIMARY KEY (id);


--
-- Name: project_multilines project_multilines_pkey; Type: CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.project_multilines
    ADD CONSTRAINT project_multilines_pkey PRIMARY KEY (id);


--
-- Name: project_points project_points_pkey; Type: CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.project_points
    ADD CONSTRAINT project_points_pkey PRIMARY KEY (id);


--
-- Name: project_polygons project_polygons_pkey; Type: CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.project_polygons
    ADD CONSTRAINT project_polygons_pkey PRIMARY KEY (id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: imagery_layers ux_imagery_ws_name; Type: CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.imagery_layers
    ADD CONSTRAINT ux_imagery_ws_name UNIQUE (workspace, layer_name);


--
-- Name: ix_imagery_date; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_imagery_date ON geodata.imagery_layers USING btree (date_captured);


--
-- Name: ix_ml_geom; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_ml_geom ON geodata.project_multilines USING gist (geom);


--
-- Name: ix_ml_project; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_ml_project ON geodata.project_multilines USING btree (project_id);


--
-- Name: ix_ml_status; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_ml_status ON geodata.project_multilines USING btree (status);


--
-- Name: ix_pg_geom; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_pg_geom ON geodata.project_polygons USING gist (geom);


--
-- Name: ix_pg_project; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_pg_project ON geodata.project_polygons USING btree (project_id);


--
-- Name: ix_pg_status; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_pg_status ON geodata.project_polygons USING btree (status);


--
-- Name: ix_pp_geom; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_pp_geom ON geodata.project_points USING gist (geom);


--
-- Name: ix_pp_project; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_pp_project ON geodata.project_points USING btree (project_id);


--
-- Name: ix_pp_status; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_pp_status ON geodata.project_points USING btree (status);


--
-- Name: ix_projects_name; Type: INDEX; Schema: geodata; Owner: postgres
--

CREATE INDEX ix_projects_name ON geodata.projects USING btree (name);


--
-- Name: project_multilines trg_ml_len_biur; Type: TRIGGER; Schema: geodata; Owner: postgres
--

CREATE TRIGGER trg_ml_len_biur BEFORE INSERT OR UPDATE ON geodata.project_multilines FOR EACH ROW EXECUTE FUNCTION geodata.compute_multiline_length();


--
-- Name: project_polygons trg_pg_area_biur; Type: TRIGGER; Schema: geodata; Owner: postgres
--

CREATE TRIGGER trg_pg_area_biur BEFORE INSERT OR UPDATE ON geodata.project_polygons FOR EACH ROW EXECUTE FUNCTION geodata.compute_polygon_area();


--
-- Name: project_multilines project_multilines_project_id_fkey; Type: FK CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.project_multilines
    ADD CONSTRAINT project_multilines_project_id_fkey FOREIGN KEY (project_id) REFERENCES geodata.projects(id) ON DELETE CASCADE;


--
-- Name: project_points project_points_project_id_fkey; Type: FK CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.project_points
    ADD CONSTRAINT project_points_project_id_fkey FOREIGN KEY (project_id) REFERENCES geodata.projects(id) ON DELETE CASCADE;


--
-- Name: project_polygons project_polygons_project_id_fkey; Type: FK CONSTRAINT; Schema: geodata; Owner: postgres
--

ALTER TABLE ONLY geodata.project_polygons
    ADD CONSTRAINT project_polygons_project_id_fkey FOREIGN KEY (project_id) REFERENCES geodata.projects(id) ON DELETE CASCADE;

