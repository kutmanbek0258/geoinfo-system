--liquibase formatted sql

--changeset daivanov:add-imagery-layers-1
CREATE TABLE geoabstraction.imagery_layers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255),
    description TEXT,
    workspace VARCHAR(128) NOT NULL,
    layer_name VARCHAR(256) NOT NULL,
    service_url VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    style VARCHAR(128),
    date_captured TIMESTAMP NOT NULL,
    crs VARCHAR(32) NOT NULL,
    characteristics JSONB,
    -- Audit Fields
    created_by              VARCHAR(255),
    created_date            TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP,
    CONSTRAINT ux_imagery_ws_name UNIQUE (workspace, layer_name)
);

CREATE INDEX ix_imagery_date ON geoabstraction.imagery_layers(date_captured);
