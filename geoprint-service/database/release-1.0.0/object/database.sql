--liquibase formatted sql

--changeset daivanov:terrain-init-1
CREATE SCHEMA IF NOT EXISTS print;

SET search_path = print, public;


