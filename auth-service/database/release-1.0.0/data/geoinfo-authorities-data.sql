--liquibase formatted sql

--changeSet geoinfo:geoinfo-authorities-data-01
INSERT INTO sso.authorities(authority_code, authority_description, system_code)
VALUES ('GEO_PROJECT_READ', 'Право на чтение проектов', 'GeoInfo'),
       ('GEO_PROJECT_CREATE', 'Право на создание проектов', 'GeoInfo'),
       ('GEO_PROJECT_UPDATE', 'Право на обновление проектов', 'GeoInfo'),
       ('GEO_PROJECT_DELETE', 'Право на удаление проектов', 'GeoInfo'),
       ('GEO_PROJECT_SHARE', 'Право на предоставление доступа к проектам', 'GeoInfo'),
       ('GEO_FEATURE_READ', 'Право на чтение объектов (точки, линии, полигоны)', 'GeoInfo'),
       ('GEO_FEATURE_CREATE', 'Право на создание объектов', 'GeoInfo'),
       ('GEO_FEATURE_UPDATE', 'Право на обновление объектов', 'GeoInfo'),
       ('GEO_FEATURE_DELETE', 'Право на удаление объектов', 'GeoInfo');
