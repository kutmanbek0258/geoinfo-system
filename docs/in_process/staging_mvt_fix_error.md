Если таблица `temp_analysis_geometries` является «сборной солянкой» для любых результатов анализов, то функция действительно должна уметь «переваривать» всё: точки, линии, полигоны и даже смешанные коллекции (GeometryCollection), не падая с ошибкой.

**Техническая реальность:**
Проблема в том, что PostGIS (и конкретно функция `ST_AsMVT`) не может сформировать валидный бинарный тайл, если ей попадается смешанная `GeometryCollection` (например, точка и полигон в одной строке). Кроме того, картографические клиенты (Mapbox GL JS, MapLibre) **требуют разделения типов геометрий для стилизации** (нельзя применить стиль `fill` к точке).

**Единственный правильный и универсальный подход** — это заставить вашу функцию возвращать один тайл, но внутри него автоматически разбивать геометрии на **три независимых подслоя** (точки, линии, полигоны). Спецификация MVT это отлично поддерживает.

---

### Универсальная функция (Разделение на подслои)

Этот код решает сразу все проблемы: он оптимизирует поиск, безопасно извлекает нужные типы геометрий (игнорируя пустые) и "склеивает" их в один бинарный ответ.

```sql
CREATE OR REPLACE FUNCTION geodata.get_staging_layer(z integer, x integer, y integer, task_uuid uuid)
    RETURNS bytea AS $$
DECLARE
    bounds geometry;
    res bytea;
BEGIN
    -- 1. Генерируем границу тайла один раз
    bounds := ST_TileEnvelope(z, x, y);

    WITH data_intersect AS (
        -- 2. Отбираем данные и сразу переводим в 3857.
        -- ST_Intersects используем с трансформацией bounds (а не таблицы), чтобы работал пространственный индекс!
        SELECT 
            t.id,
            t.properties,
            ST_Transform(t.geom, 3857) as geom_3857
        FROM geodata.temp_analysis_geometries t
        WHERE t.task_id = task_uuid
          AND ST_Intersects(t.geom, ST_Transform(bounds, 4326)) -- Замените 4326 на SRID вашей таблицы, если он другой
    ),
    layers AS (
        -- 3. Собираем слой ТОЧЕК (ST_CollectionExtract с кодом 1)
        SELECT ST_AsMVT(pt.*, 'staging_points') AS mvt
        FROM (
            SELECT id, properties, ST_AsMVTGeom(ST_CollectionExtract(geom_3857, 1), bounds) AS geom
            FROM data_intersect
        ) pt
        WHERE geom IS NOT NULL

        UNION ALL

        -- 4. Собираем слой ЛИНИЙ (ST_CollectionExtract с кодом 2)
        SELECT ST_AsMVT(line.*, 'staging_lines') AS mvt
        FROM (
            SELECT id, properties, ST_AsMVTGeom(ST_CollectionExtract(geom_3857, 2), bounds) AS geom
            FROM data_intersect
        ) line
        WHERE geom IS NOT NULL

        UNION ALL

        -- 5. Собираем слой ПОЛИГОНОВ (ST_CollectionExtract с кодом 3)
        SELECT ST_AsMVT(poly.*, 'staging_polygons') AS mvt
        FROM (
            SELECT id, properties, ST_AsMVTGeom(ST_CollectionExtract(geom_3857, 3), bounds) AS geom
            FROM data_intersect
        ) poly
        WHERE geom IS NOT NULL
    )
    -- 6. Склеиваем все непустые слои в один бинарный тайл (MVT)
    SELECT string_agg(mvt, ''::bytea) INTO res FROM layers;

    RETURN res;
END; $$ LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE;

```

### Как это работает:

1. **`ST_CollectionExtract(geom, N)`**: Это магия, которая делает функцию универсальной. Она берет любую геометрию (включая смешанные коллекции) и достает из нее только нужный тип: `1` = точки, `2` = линии, `3` = полигоны.
2. **`WHERE geom IS NOT NULL`**: Если в тайле нет линий, функция `ST_AsMVTGeom` вернет `NULL`. Мы просто отфильтровываем пустые результаты, чтобы не генерировать пустые слои.
3. **`string_agg(mvt, ''::bytea)`**: Объединяет сгенерированные слои MVT в единый `bytea` файл, который отдается клиенту.

---

### ⚠️ Важное изменение для фронтенда

Поскольку теперь тайл возвращает не один монолитный слой `staging_layer`, а три специализированных (`staging_points`, `staging_lines`, `staging_polygons`), вам **нужно обновить код на клиенте (на карте)**.

Вам потребуется добавить источник данных (source) один раз, но создать для него **три разных слоя (layers)** для отрисовки.

**Пример для Mapbox / MapLibre GL JS:**

```javascript
// Источник остается один
map.addSource('staging-source', {
    type: 'vector',
    tiles: [`http://your-server/geodata.get_staging_layer/{z}/{x}/{y}.pbf?task_uuid=${taskId}`]
});

// Добавляем слой для полигонов
map.addLayer({
    'id': 'layer-polygons',
    'type': 'fill',
    'source': 'staging-source',
    'source-layer': 'staging_polygons', // <- Имя из SQL функции
    'paint': { 'fill-color': '#0080ff', 'fill-opacity': 0.5 }
});

// Добавляем слой для линий
map.addLayer({
    'id': 'layer-lines',
    'type': 'line',
    'source': 'staging-source',
    'source-layer': 'staging_lines', // <- Имя из SQL функции
    'paint': { 'line-color': '#ff0000', 'line-width': 2 }
});

// Добавляем слой для точек
map.addLayer({
    'id': 'layer-points',
    'type': 'circle',
    'source': 'staging-source',
    'source-layer': 'staging_points', // <- Имя из SQL функции
    'paint': { 'circle-radius': 5, 'circle-color': '#00ff00' }
});

```

Какой картографический клиент (Mapbox, MapLibre, Leaflet, OpenLayers) вы используете для рендеринга на фронтенде?