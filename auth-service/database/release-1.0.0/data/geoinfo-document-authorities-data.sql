--liquibase formatted sql

--changeSet geoinfo:geoinfo-document-authorities-data-01
INSERT INTO sso.authorities(authority_code, authority_description, system_code)
VALUES ('DOCUMENT_CREATE', 'Право на создание/загрузку документов', 'GeoInfo'),
       ('DOCUMENT_READ', 'Право на чтение/скачивание документов', 'GeoInfo'),
       ('DOCUMENT_UPDATE', 'Право на обновление метаданных документов', 'GeoInfo'),
       ('DOCUMENT_DELETE', 'Право на удаление документов', 'GeoInfo'),
       ('DOCUMENT_EDIT_ONLINE', 'Право на онлайн-редактирование документов через OnlyOffice', 'GeoInfo');
