# Анализ получения BBox проекта и приближения карты на фронтенде

Ниже приведен детальный анализ того, как система вычисляет, передает и использует географические границы (BBox) проекта при инициализации и работе с интерактивной картой.

---

## 1. Как вычисляется и получается BBox проекта

BBox проекта рассчитывается динамически на стороне базы данных PostGIS и передается через API-интерфейсы.

### 1.1. Автоматический расчет границ векторных объектов (БД)
Для каждого отдельного геообъекта (точки, линии, полигона) границы вычисляются в СУБД PostgreSQL/PostGIS с помощью триггера [add_bbox_to_geo_objects.sql](file:///C:/Users/admin/Documents/dev/geoinfo-system/geodata-service/database/release-1.1.0/object/add_bbox_to_geo_objects.sql#L8-L37):
1. Триггерная функция `geodata.update_geo_object_bbox()` перехватывает операции вставки или обновления геометрии (`geom`) в таблицах `project_points`, `project_multilines` и `project_polygons`.
2. Она выполняет расчет огибающего прямоугольника с помощью функции `ST_Envelope`:
   ```sql
   NEW.bbox := ST_SetSRID(ST_Envelope(NEW.geom), 4326);
   ```

### 1.2. Вычисление BBox всего проекта
В репозитории [ProjectRepository.java](file:///C:/Users/admin/Documents/dev/geoinfo-system/geodata-service/src/main/java/kg/geoinfo/system/geodataservice/repository/ProjectRepository.java#L21-L28) объявлен нативный пространственный SQL-запрос для объединения и вычисления огибающего полигона для всех дочерних векторов проекта:
```sql
SELECT ST_SetSRID(ST_Envelope(ST_Collect(geom)), 4326) FROM (
  SELECT geom FROM geodata.project_points WHERE project_id = :projectId 
  UNION ALL 
  SELECT geom FROM geodata.project_multilines WHERE project_id = :projectId 
  UNION ALL 
  SELECT geom FROM geodata.project_polygons WHERE project_id = :projectId
) as all_geoms
```
* `ST_Collect(geom)`: Собирает все геометрии проекта в один Multi-объект.
* `ST_Envelope(...)`: Строит минимальный огибающий прямоугольник.
* `ST_SetSRID(..., 4326)`: Задает систему координат WGS84.

### 1.3. Пути получения данных и ограничения (Бэкенд-Фронтенд)
На стороне [ProjectService.java](file:///C:/Users/admin/Documents/dev/geoinfo-system/geodata-service/src/main/java/kg/geoinfo/system/geodataservice/service/ProjectService.java):
* **Одиночный запрос (`findById`):** Поле `bbox` в `ProjectDto` явно заполняется вызовом метода репозитория:
  ```java
  dto.setBbox(projectRepository.calculateProjectBBox(id));
  ```
* **Пакетный запрос (`findByCondition` / `page-query`):** Эндпоинт `/api/geodata/project/page-query` возвращает список проектов, но **не выполняет** расчет `bbox` для каждого проекта.

> [!WARNING]
> **Архитектурное ограничение на фронтенде:**
> В хранилище Vuex [geodata.store.ts](file:///C:/Users/admin/Documents/dev/geoinfo-system/frontend/src/store/geodata.store.ts) проекты запрашиваются только пакетом через экшен `fetchProjects`, вызывающий метод `getProjects` (который обращается к `/page-query`). 
> Метод получения одного проекта по ID `getProjectById` присутствует в [geodata.service.ts](file:///C:/Users/admin/Documents/dev/geoinfo-system/frontend/src/services/geodata.service.ts#L18), но **не вызывается ни в одном экшене Vuex или Vue-компоненте**.
> Из-за этого поле `selectedProject?.bbox` на фронтенде **всегда равно `undefined` / `null`**, и первая ветка приближения в `zoomToExtent` никогда не срабатывает.

---

## 2. Как реализовано приближение карты к BBox на фронтенде

Фронтенд обрабатывает приближение в двух режимах отображения: 2D (OpenLayers) и 3D (Cesium).

### 2.1. Режим 2D: OpenLayers (`MapComponentMVT.vue`)
При загрузке карты или клике на кнопку «Zoom to extent» вызывается метод `zoomToExtent` из [MapComponentMVT.vue](file:///C:/Users/admin/Documents/dev/geoinfo-system/frontend/src/components/map/MapComponentMVT.vue#L261-L289):

```typescript
const zoomToExtent = () => {
  if (!map.value) return;

  // 1. Попытка использовать предвычисленный bbox проекта из Vuex (Всегда null из-за ограничений page-query)
  const selectedProject = store.state.geodata.projects?.content.find((p: any) => p.id === props.projectId);
  if (selectedProject?.bbox) {
    const features = geoJsonFormat.readFeatures(selectedProject.bbox, { dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3857' });
    if (features.length > 0) {
      const extent = features[0].getGeometry()!.getExtent();
      map.value.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000 });
      return;
    }
  }

  // 2. Резервный сценарий (Fallback): динамический расчет границ на клиенте
  const allObjects = [...points.value, ...multilines.value, ...polygons.value];
  if (allObjects.length === 0) return;
  const extent = createEmpty();
  allObjects.forEach(obj => {
    if (obj.geom) {
      const readFeatures = geoJsonFormat.readFeatures(obj.geom, { dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3857' });
      (Array.isArray(readFeatures) ? readFeatures : [readFeatures]).forEach(f => {
        if (f) extend(extent, f.getGeometry()!.getExtent());
      });
    }
  });
  if (extent && extent.every(isFinite) && (extent[0] !== Infinity)) {
    map.value.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000 });
  }
};
```

### 2.2. Режим 3D: Cesium (`CesiumMapComponent.vue`)
В 3D-режиме функция `zoomToExtent` из [CesiumMapComponent.vue](file:///C:/Users/admin/Documents/dev/geoinfo-system/frontend/src/components/map/CesiumMapComponent.vue#L326-L355) работает аналогично, но с использованием камеры Cesium:

```typescript
const zoomToExtent = () => {
  const v = viewer.value;
  if (!v) return;

  // 1. Попытка использовать bbox проекта
  const selectedProject = store.state.geodata.projects?.content.find((p: any) => p.id === props.projectId);
  if (selectedProject?.bbox && selectedProject.bbox.type === 'Polygon') {
    const coords = (selectedProject.bbox.coordinates as number[][][])[0];
    const lons = coords.map((c: any) => c[0]);
    const lats = coords.map((c: any) => c[1]);
    const rect = Cesium.Rectangle.fromDegrees(Math.min(...lons), Math.min(...lats), Math.max(...lons), Math.max(...lats));
    v.camera.flyTo({ destination: rect, duration: 2.0 });
    return;
  }

  // 2. Резервный сценарий: расчет bbox с помощью Turf.js
  const allGeoms = [...points.value.map(p => p.geom), ...multilines.value.map(l => l.geom), ...polygons.value.map(p => p.geom)]
    .map(g => { if (!g) return null; try { return typeof g === 'string' ? JSON.parse(g) : g; } catch(e) { return null; } })
    .filter(g => g && g.type && g.coordinates);
  if (allGeoms.length > 0) {
    try {
      const coll = turf.featureCollection(allGeoms.map(g => turf.feature(g)));
      const bbox = turf.bbox(coll);
      const rect = Cesium.Rectangle.fromDegrees(bbox[0], bbox[1], bbox[2], bbox[3]);
      const margin = Math.max(rect.width, rect.height, 0.01) * 0.3; // Добавление 30% полей
      v.camera.flyTo({ destination: new Cesium.Rectangle(rect.west-margin, rect.south-margin, rect.east+margin, rect.north+margin), duration: 2.0 });
      return;
    } catch (e) { console.warn("Turf bbox failed", e); }
  }
  if (v.entities.values.length > 0) v.zoomTo(v.entities);
};
```

---

## 3. Проблемы текущего подхода и предложения по оптимизации

> [!WARNING]
> **Проблема 1: Отсутствие автозума для пустых/растровых проектов**
> Если проект только что создан и в нем еще нет векторных объектов (или он содержит только растровые слои), резервный сценарий расчета границ на клиенте возвращает пустой экстент. В результате карта остается сфокусированной на координатах по умолчанию `[0, 0]` с зумом `2`.

> [!IMPORTANT]
> **Проблема 2: Избыточный трафик и нагрузка на процессор клиента**
> Для проектов с десятками тысяч векторов клиентский расчет экстента через парсинг GeoJSON в цикле приводит к заметным задержкам (блокировка UI-потока) при открытии карты.

### Рекомендации по оптимизации:

1. **Решение А (Рекомендуемое): Вызов детального проекта при открытии карты**
   При переходе на страницу карты проекта в [ProjectMapView.vue](file:///C:/Users/admin/Documents/dev/geoinfo-system/frontend/src/views/ProjectMapView.vue) добавить вызов метода загрузки детальной информации проекта по ID:
   ```typescript
   // В store добавить экшен:
   async fetchProjectById({ commit }, projectId: string) {
       const response = await geodataService.getProjectById(projectId);
       commit('UPDATE_PROJECT_IN_LIST', response.data);
   }
   ```
   Это вернет предвычисленный БД полигон `bbox` и позволит мгновенно и плавно приближать карту на OpenLayers/Cesium к границам проекта без необходимости грузить и парсить все его векторы.

2. **Решение Б: Включение BBox в список проектов**
   Добавить расчет `calculateProjectBBox` в метод `findByCondition` класса `ProjectService.java` на бэкенде. Это увеличит время отдачи списка проектов (так как БД будет делать `ST_Collect` для каждого проекта в списке), но избавит от необходимости делать отдельные HTTP-запросы.
