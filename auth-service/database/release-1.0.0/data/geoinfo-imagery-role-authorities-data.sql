--liquibase formatted sql

--changeSet geoinfo:geoinfo-imagery-role-authorities-data-01
-- Права для ROLE_ADMIN (все права на слои)
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_ADMIN'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code IN (
    'IMAGERY_LAYER_CREATE',
    'IMAGERY_LAYER_READ',
    'IMAGERY_LAYER_UPDATE',
    'IMAGERY_LAYER_DELETE'
);

-- Права для ROLE_EDITOR (только чтение слоев)
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_EDITOR'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code = 'IMAGERY_LAYER_READ';

-- Права для ROLE_VIEWER (только чтение слоев)
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_VIEWER'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code = 'IMAGERY_LAYER_READ';
