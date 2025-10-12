--liquibase formatted sql

--changeSet geoinfo:geoinfo-role-authorities-data-01
-- Права для ROLE_ADMIN (все права)
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_ADMIN'),
    a.authority_id
FROM sso.authorities a WHERE a.system_code = 'GeoInfo';

-- Права для ROLE_EDITOR
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_EDITOR'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code IN (
    'GEO_PROJECT_READ',
    'GEO_PROJECT_CREATE',
    'GEO_PROJECT_UPDATE',
    'GEO_PROJECT_DELETE',
    'GEO_PROJECT_SHARE',
    'GEO_FEATURE_READ',
    'GEO_FEATURE_CREATE',
    'GEO_FEATURE_UPDATE',
    'GEO_FEATURE_DELETE'
);

-- Права для ROLE_VIEWER
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_VIEWER'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code IN (
    'GEO_PROJECT_READ',
    'GEO_FEATURE_READ'
);
