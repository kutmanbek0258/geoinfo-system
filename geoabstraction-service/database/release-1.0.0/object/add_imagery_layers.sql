CREATE TABLE geoabstraction.imagery_layers (
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
    characteristics JSONB DEFAULT '{}',
    created_by character varying(255),
    created_date timestamp without time zone,
    last_modified_by character varying(255),
    last_modified_date timestamp without time zone
);