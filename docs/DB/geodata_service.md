## **ФИНАЛЬНАЯ СХЕМА БД POSTGIS (Geo Data Service)**

\-- \=====================================================================  
\-- Система интерактивной карты — конечная БД (PostgreSQL \+ PostGIS)  
\-- Версия: v4-final  
\-- Требования: PostgreSQL 13+ (рекоменд.), расширения: postgis, pgcrypto  
\-- \=====================================================================  
\-- \---------- БАЗОВЫЕ НАСТРОЙКИ / РАСШИРЕНИЯ \----------  
\-- Создание схемы для изоляции таблиц геоданных  
CREATE SCHEMA IF NOT EXISTS geodata;  
\-- Установка расширений  
CREATE EXTENSION IF NOT EXISTS plpgsql;  
CREATE EXTENSION IF NOT EXISTS postgis;  
CREATE EXTENSION IF NOT EXISTS pgcrypto; \-- Для gen\_random\_uuid()  
SET search\_path \= geodata, public;

\-- \=====================================================================  
\-- GEODATA: ТАБЛИЦЫ  
\-- \=====================================================================  
\-- \-------------------- PROJECTS (Корневая сущность) \--------------------  
\-- Таблица для логического группирования геообъектов  
CREATE TABLE IF NOT EXISTS projects (  
id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
name VARCHAR(256) NOT NULL,  
description TEXT,  
start\_date DATE,  
end\_date DATE,  
created\_by VARCHAR(255),  
created\_date TIMESTAMP,  
last\_modified\_by VARCHAR(255),  
last\_modified\_date TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix\_projects\_name ON projects(name);  
\-- \-------------------- POINTS (Точечные объекты) \--------------------  
CREATE TABLE IF NOT EXISTS project\_points (  
id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
project\_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
name VARCHAR(256),  
description TEXT,  
status VARCHAR(16),  
geom geometry(Point, 4326\) NOT NULL, \-- Координаты: WGS 84  
image\_link VARCHAR(1000), \-- Ссылка на превью в MinIO  
characteristics JSONB, \-- Неструктурированные данные. Например, для типа "camera" здесь будут храниться {"type": "camera", "ip_address": "192.168.1.10", "port": 8000, "login": "admin", "password": "password"}.  
created\_by VARCHAR(255),  
created\_date TIMESTAMP,  
last\_modified\_by VARCHAR(255),  
last\_modified\_date TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix\_pp\_project ON project\_points(project\_id);  
CREATE INDEX IF NOT EXISTS ix\_pp\_status ON project\_points(status);  
CREATE INDEX IF NOT EXISTS ix\_pp\_geom ON project\_points USING GIST (geom); \-- Пространственный индекс  
\-- \-------------------- MULTILINES (Линейные объекты) \--------------------  
CREATE TABLE IF NOT EXISTS project\_multilines (  
id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
project\_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
name VARCHAR(256),  
description TEXT,  
status VARCHAR(16),  
length\_m DOUBLE PRECISION, \-- Авторасчёт суммарной длины (в метрах)  
geom geometry(MultiLineString, 4326\) NOT NULL,  
characteristics JSONB,  
created\_by VARCHAR(255),  
created\_date TIMESTAMP,  
last\_modified\_by VARCHAR(255),  
last\_modified\_date TIMESTAMP,  
CONSTRAINT chk\_ml\_length\_nonneg CHECK (length\_m IS NULL OR length\_m \>= 0\)  
);  
CREATE INDEX IF NOT EXISTS ix\_ml\_project ON project\_multilines(project\_id);  
CREATE INDEX IF NOT EXISTS ix\_ml\_status ON project\_multilines(status);  
CREATE INDEX IF NOT EXISTS ix\_ml\_geom ON project\_multilines USING GIST (geom);  
\-- \-------------------- POLYGONS (Полигональные объекты) \--------------------  
CREATE TABLE IF NOT EXISTS project\_polygons (  
id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
project\_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
name VARCHAR(256),  
description TEXT,  
status VARCHAR(16),  
area\_m2 DOUBLE PRECISION, \-- Авторасчёт площади (в метрах^2)  
geom geometry(Polygon, 4326\) NOT NULL,  
characteristics JSONB,  
created\_by VARCHAR(255),  
created\_date TIMESTAMP,  
last\_modified\_by VARCHAR(255),  
last\_modified\_date TIMESTAMP,  
CONSTRAINT chk\_pg\_area\_nonneg CHECK (area\_m2 IS NULL OR area\_m2 \>= 0\)  
);  
CREATE INDEX IF NOT EXISTS ix\_pg\_project ON project\_polygons(project\_id);  
CREATE INDEX IF NOT EXISTS ix\_pg\_status ON project\_polygons(status);  
CREATE INDEX IF NOT EXISTS ix\_pg\_geom ON project\_polygons USING GIST (geom);  
\-- \-------------------- IMAGERY LAYERS (Реестр слоёв GeoServer) \--------------------  
CREATE TABLE IF NOT EXISTS imagery\_layers (  
id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
name VARCHAR(256),  
description TEXT,  
workspace VARCHAR(128) NOT NULL,  
layer\_name VARCHAR(256) NOT NULL, \-- Имя слоя в GeoServer  
service\_url TEXT NOT NULL, \-- Базовый WMS/WMTS endpoint  
style VARCHAR(128),  
date\_captured DATE NOT NULL,  
crs VARCHAR(32) NOT NULL DEFAULT 'EPSG:3857',  
status VARCHAR(16),  
created\_by VARCHAR(255),  
created\_date TIMESTAMP,  
last\_modified\_by VARCHAR(255),  
last\_modified\_date TIMESTAMP,  
CONSTRAINT ux\_imagery\_ws\_name UNIQUE (workspace, layer\_name)  
);  
CREATE INDEX IF NOT EXISTS ix\_imagery\_date ON imagery\_layers(date\_captured);  
\-- \=====================================================================  
\-- GEODATA: ФУНКЦИИ/ТРИГГЕРЫ (для авторасчёта)  
\-- \=====================================================================  
\-- Функция подсчёта длины MultiLine с использованием географии (метры)  
CREATE OR REPLACE FUNCTION compute\_multiline\_length()  
RETURNS trigger AS $$  
BEGIN  
NEW.length\_m := ST\_Length(NEW.geom::geography);  
RETURN NEW;  
END; $$ LANGUAGE plpgsql;  
\-- Функция подсчёта площади Polygon с использованием географии (метры^2)  
CREATE OR REPLACE FUNCTION compute\_polygon\_area()  
RETURNS trigger AS $$  
BEGIN  
NEW.area\_m2 := ST\_Area(NEW.geom::geography);  
RETURN NEW;  
END; $$ LANGUAGE plpgsql;  
\-- Триггеры пересчёта длины/площади ПЕРЕД INSERT ИЛИ UPDATE  
DROP TRIGGER IF EXISTS trg\_ml\_len\_biur ON project\_multilines;  
CREATE TRIGGER trg\_ml\_len\_biur  
BEFORE INSERT OR UPDATE ON project\_multilines  
FOR EACH ROW EXECUTE FUNCTION compute\_multiline\_length();  
DROP TRIGGER IF EXISTS trg\_pg\_area\_biur ON project\_polygons;  
CREATE TRIGGER trg\_pg\_area\_biur  
BEFORE INSERT OR UPDATE ON project\_polygons  
FOR EACH ROW EXECUTE FUNCTION compute\_polygon\_area();  
\-- \=====================================================================  
\-- ВОЗВРАТ search\_path  
\-- \=====================================================================  
SET search\_path \= public;