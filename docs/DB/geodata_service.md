## **ФИНАЛЬНАЯ СХЕМА БД POSTGIS (Geo Data Service)**

База данных для управления векторными проектами, иерархией папок, гео-объектами (точки, линии, полигоны), проектными растрами, глобальными подложками, стилями растров и рельефами.

```sql
CREATE SCHEMA IF NOT EXISTS geodata;  
SET search_path = geodata, public;
```

---

### **1. Таблица projects (Проекты)**
Корневая сущность для группировки всех геопространственных данных.

```sql
CREATE TABLE IF NOT EXISTS projects (  
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    name               VARCHAR(256) NOT NULL,  
    description        TEXT,  
    start_date         TIMESTAMP,  
    end_date           TIMESTAMP,  
    created_by         VARCHAR(255),  
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  
    last_modified_by   VARCHAR(255),  
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix_projects_name ON projects(name);
``` 

---

### **2. Таблица project_access (Права доступа к проектам)**
Связующая таблица для реализации прав доступа (RBAC/Project sharing).

```sql
CREATE TABLE IF NOT EXISTS project_access (
    project_id       UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_email       VARCHAR(255) NOT NULL,
    permission_level VARCHAR(50) NOT NULL,
    PRIMARY KEY (project_id, user_email)
);
```

---

### **3. Таблица layers (Слои проекта)**
Новая корневая сущность внутри проекта. Разделяет данные на векторные и растровые.

```sql
CREATE TABLE IF NOT EXISTS layers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    type               VARCHAR(50) NOT NULL, -- VECTOR / RASTER
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_layers_project ON layers(project_id);
```

---

### **4. Таблица folders (Папки объектов)**
Иерархическая структура для группировки гео-объектов внутри слоя.

```sql
CREATE TABLE IF NOT EXISTS folders (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id           UUID NOT NULL REFERENCES layers(id) ON DELETE CASCADE,
    parent_id          UUID REFERENCES folders(id) ON DELETE SET NULL,
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_folders_layer ON folders(layer_id);
CREATE INDEX IF NOT EXISTS ix_folders_parent ON folders(parent_id);
```

---

### **5. Таблица project_points (Точечные объекты)**
Геометрия хранится в 3D формате (MultiPointZ).

```sql 
CREATE TABLE IF NOT EXISTS project_points (  
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    project_id         UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
    layer_id           UUID REFERENCES layers(id) ON DELETE SET NULL,
    folder_id          UUID REFERENCES folders(id) ON DELETE SET NULL,
    name               VARCHAR(256),  
    description        TEXT,  
    status             VARCHAR(16),  
    geom               GEOMETRY(MultiPointZ, 4326) NOT NULL,  
    image_url          VARCHAR(1000),  
    characteristics    JSONB DEFAULT '{}',  
    bbox               GEOMETRY(Polygon, 4326),
    created_by         VARCHAR(255),  
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  
    last_modified_by   VARCHAR(255),  
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP  
);  
CREATE INDEX IF NOT EXISTS ix_pp_project ON project_points(project_id);
CREATE INDEX IF NOT EXISTS ix_pp_layer   ON project_points(layer_id);
CREATE INDEX IF NOT EXISTS ix_pp_folder  ON project_points(folder_id);
CREATE INDEX IF NOT EXISTS ix_pp_geom    ON project_points USING GIST (geom);
``` 

---

### **6. Таблица project_multilines (Линейные объекты)**
Геометрия хранится в 3D формате (MultiLineStringZ). Длина вычисляется триггером.

```sql 
CREATE TABLE IF NOT EXISTS project_multilines (  
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    project_id         UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
    layer_id           UUID REFERENCES layers(id) ON DELETE SET NULL,
    folder_id          UUID REFERENCES folders(id) ON DELETE SET NULL,
    name               VARCHAR(256),  
    description        TEXT,  
    status             VARCHAR(16),  
    length_m           DOUBLE PRECISION,  
    geom               GEOMETRY(MultiLineStringZ, 4326) NOT NULL,  
    image_url          VARCHAR(1000),  
    characteristics    JSONB DEFAULT '{}',  
    bbox               GEOMETRY(Polygon, 4326),
    created_by         VARCHAR(255),  
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  
    last_modified_by   VARCHAR(255),  
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ml_length_nonneg CHECK (length_m IS NULL OR length_m >= 0)
);  
CREATE INDEX IF NOT EXISTS ix_ml_project ON project_multilines(project_id);
CREATE INDEX IF NOT EXISTS ix_ml_layer   ON project_multilines(layer_id);
CREATE INDEX IF NOT EXISTS ix_ml_folder  ON project_multilines(folder_id);
CREATE INDEX IF NOT EXISTS ix_ml_geom    ON project_multilines USING GIST (geom);
```

---

### **7. Таблица project_polygons (Полигональные объекты)**
Геометрия хранится в 3D формате (MultiPolygonZ). Площадь вычисляется триггером.

```sql
CREATE TABLE IF NOT EXISTS project_polygons (  
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),  
    project_id         UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,  
    layer_id           UUID REFERENCES layers(id) ON DELETE SET NULL,
    folder_id          UUID REFERENCES folders(id) ON DELETE SET NULL,
    name               VARCHAR(256),  
    description        TEXT,  
    status             VARCHAR(16),  
    area_m2            DOUBLE PRECISION,  
    geom               GEOMETRY(MultiPolygonZ, 4326) NOT NULL,  
    image_url          VARCHAR(1000),  
    characteristics    JSONB DEFAULT '{}',  
    bbox               GEOMETRY(Polygon, 4326),
    created_by         VARCHAR(255),  
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  
    last_modified_by   VARCHAR(255),  
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pg_area_nonneg CHECK (area_m2 IS NULL OR area_m2 >= 0)
);
CREATE INDEX IF NOT EXISTS ix_pg_project ON project_polygons(project_id);
CREATE INDEX IF NOT EXISTS ix_pg_layer   ON project_polygons(layer_id);
CREATE INDEX IF NOT EXISTS ix_pg_folder  ON project_polygons(folder_id);
CREATE INDEX IF NOT EXISTS ix_pg_geom    ON project_polygons USING GIST (geom);
```

---

### **8. Таблица project_rasters (Растровые данные проекта)**
Метаданные загруженных и обработанных растровых сцен, привязанных к растровому слою проекта.

```sql
CREATE TABLE IF NOT EXISTS project_rasters (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id           UUID NOT NULL REFERENCES layers(id) ON DELETE CASCADE,
    folder_id          UUID REFERENCES folders(id) ON DELETE SET NULL,
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    cog_object_key     VARCHAR(512),
    bbox               GEOMETRY(MultiPolygon, 4326),
    crs                VARCHAR(32),
    colormap_id        VARCHAR(100),
    resampling         VARCHAR(50),
    date_captured      TIMESTAMP,
    status             VARCHAR(50) NOT NULL,
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_proj_raster_layer ON project_rasters(layer_id);
CREATE INDEX IF NOT EXISTS ix_proj_raster_folder ON project_rasters(folder_id);
```

---

### **9. Таблица raster_layers (Глобальные растровые подложки)**
Спецификация растровых подложек, доступных глобально всем проектам.

```sql
CREATE TABLE IF NOT EXISTS raster_layers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    cog_object_key     VARCHAR(512),
    bbox               GEOMETRY(MultiPolygon, 4326),
    crs                VARCHAR(32),
    colormap_id        VARCHAR(100),
    resampling         VARCHAR(50),
    date_captured      TIMESTAMP,
    status             VARCHAR(50) NOT NULL,
    characteristics    JSONB DEFAULT '{}',
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### **10. Таблица raster_styles (Цветовые шкалы)**
Спецификации стилей рендеринга для растров.

```sql
CREATE TABLE IF NOT EXISTS raster_styles (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(100) UNIQUE NOT NULL,
    title              VARCHAR(256) NOT NULL,
    type               VARCHAR(50) NOT NULL DEFAULT 'ramp',
    config             JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_system          BOOLEAN NOT NULL DEFAULT FALSE,
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### **11. Таблица terrain_layers (Глобальные 3D рельефы)**
Метаданные готовых 3D Cesium мешей рельефа.

```sql
CREATE TABLE IF NOT EXISTS terrain_layers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id             UUID,
    output_prefix      VARCHAR(512),
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    terrain_url        VARCHAR(512),
    cog_object_key     VARCHAR(512),
    status             VARCHAR(50),
    is_visible         BOOLEAN DEFAULT TRUE,
    created_by         VARCHAR(255),
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by   VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### **12. Таблица temp_analysis_geometries (Временные результаты анализов)**
Служит буфером для динамического MVT отображения предпросмотра слоев ГИС-анализа.

```sql
CREATE TABLE IF NOT EXISTS temp_analysis_geometries (
    id                 BIGSERIAL PRIMARY KEY,
    task_id            UUID NOT NULL,
    geom               GEOMETRY(GeometryZ, 4326),
    properties         JSONB DEFAULT '{}'::jsonb,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_temp_geom_task_id ON temp_analysis_geometries(task_id);
CREATE INDEX IF NOT EXISTS idx_temp_geom_gist ON temp_analysis_geometries USING GIST(geom);
```

SET search_path = public;
