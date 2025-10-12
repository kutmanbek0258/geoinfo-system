--liquibase formatted sql

--changeSet geoinfo:geoinfo-imagery-authorities-data-01
INSERT INTO sso.authorities(authority_code, authority_description, system_code)
VALUES ('IMAGERY_LAYER_CREATE', 'Право на создание слоев изображений', 'GeoInfo'),
       ('IMAGERY_LAYER_READ', 'Право на чтение слоев изображений', 'GeoInfo'),
       ('IMAGERY_LAYER_UPDATE', 'Право на обновление слоев изображений', 'GeoInfo'),
       ('IMAGERY_LAYER_DELETE', 'Право на удаление слоев изображений', 'GeoInfo');
