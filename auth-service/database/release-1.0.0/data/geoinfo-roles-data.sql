--liquibase formatted sql

--changeSet geoinfo:geoinfo-roles-data-01
INSERT INTO sso.roles(role_code, role_description, system_code)
VALUES ('ROLE_ADMIN', 'Роль администратора GeoInfo-System', 'GeoInfo'),
       ('ROLE_EDITOR', 'Роль редактора GeoInfo-System', 'GeoInfo'),
       ('ROLE_VIEWER', 'Роль просмотрщика GeoInfo-System', 'GeoInfo');
