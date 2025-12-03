--liquibase formatted sql

--changeSet geoinfo:geoinfo-users-role-data-01
INSERT INTO sso.user_roles(user_id, role_id)
SELECT user_id, (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_ADMIN')
FROM sso.users
ON CONFLICT DO NOTHING;

INSERT INTO sso.user_roles(user_id, role_id)
SELECT user_id, (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_EDITOR')
FROM sso.users
ON CONFLICT DO NOTHING;

INSERT INTO sso.user_roles(user_id, role_id)
SELECT user_id, (SELECT role_id FROM sso.roles WHERE role_code = 'ROLE_VIEWER')
FROM sso.users
ON CONFLICT DO NOTHING;