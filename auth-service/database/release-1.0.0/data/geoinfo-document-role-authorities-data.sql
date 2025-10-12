--liquibase formatted sql

--changeSet geoinfo:geoinfo-document-role-authorities-data-01
-- Права для ROLE_ADMIN (все права на документы)
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_ADMIN'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code IN (
    'DOCUMENT_CREATE',
    'DOCUMENT_READ',
    'DOCUMENT_UPDATE',
    'DOCUMENT_DELETE',
    'DOCUMENT_EDIT_ONLINE'
);

-- Права для ROLE_EDITOR (все права на документы)
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_EDITOR'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code IN (
    'DOCUMENT_CREATE',
    'DOCUMENT_READ',
    'DOCUMENT_UPDATE',
    'DOCUMENT_DELETE',
    'DOCUMENT_EDIT_ONLINE'
);

-- Права для ROLE_VIEWER (только чтение)
INSERT INTO sso.role_authorities (role_id, authority_id)
SELECT
    (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_VIEWER'),
    a.authority_id
FROM sso.authorities a WHERE a.authority_code = 'DOCUMENT_READ';
