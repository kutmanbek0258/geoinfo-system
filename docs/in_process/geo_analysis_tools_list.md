Для интеграции в разработанную модульную архитектуру ядра `geoanalysis-worker` подготовлен систематизированный список геоаналитических инструментов (плагинов). Каждый инструмент спроектирован как изолированный класс, наследуемый от `BaseGeoPlugin`, и оперирует исключительно локальными файлами в RAM-диске (`tmpfs`), обмениваясь метаданными через Kafka.

Список разделен на логические категории в соответствии с классическим делением инструментов геообработки (Processing Toolbox) уровня профессиональных ГИС.

---

### Категория 1: Анализ рельефа и поверхностей (Terrain Analysis)

Инструменты этой группы используют в качестве входных данных матрицы высот (ЦМР / DEM / СРТМ) и генерируют деривативы рельефа.

| Идентификатор (`pluginName`) | Описание алгоритма | Ключевой стек | Входные данные (Inputs) | Параметры (Parameters) | Выходные артефакты (Outputs) |
| --- | --- | --- | --- | --- | --- |
| `generate_contours` | **Генерация изолиний.** Расчет линий равных высот по матрице DEM. | `GDAL (ContourGenerate)`, `pyogrio` | `dem_raster` (GeoTIFF) | `interval` (float), `base_elevation` (float) | `contours` (GeoJSON/GeoPackage) |
| `slope_aspect` | **Крутизна и экспозиция уклонов.** Расчет углов наклона поверхности и их ориентации по сторонам света. | `gdaldem (API)`, `numpy` | `dem_raster` (GeoTIFF) | `mode` ("slope" / "aspect"), `units` ("degrees" / "percent") | `terrain_analysis_raster` (GeoTIFF) |
| `viewshed_analysis` | **Зоны видимости.** Определение прямой видимости из точки с учетом рельефа и высоты объекта. | `GDAL (ViewshedGenerate)` | `dem_raster` (GeoTIFF) | `observer_x` (float), `observer_y` (float), `observer_height` (float), `max_distance` (float) | `viewshed_mask` (Бинарный GeoTIFF) |
| `hillshade` | **Теневая отмывка рельефа.** Моделирование освещенности поверхности солнцем для 2D-визуализации рельефа. | `gdaldem (API)` | `dem_raster` (GeoTIFF) | `azimuth` (float: 0-360), `altitude` (float: 0-90) | `hillshade_raster` (8-bit GeoTIFF) |

---

### Категория 2: Дистанционное зондирование и растровая математика (Remote Sensing & Raster Math)

Инструменты для спектрального анализа мультиспектральных космических снимков (Sentinel-2, Landsat) и попиксельных вычислений.

| Идентификатор (`pluginName`) | Описание алгоритма | Ключевой стек | Входные данные (Inputs) | Параметры (Parameters) | Выходные артефакты (Outputs) |
| --- | --- | --- | --- | --- | --- |
| `spectral_indices` | **Расчет вегетационных/гидрологических индексов.** Автоматический расчет NDVI, NDWI, NBR, NDRE по формулам отражения каналов. | `rasterio`, `numpy` | `bands` (Словарь путей к каналам: `{"red": "...", "nir": "..."}`) | `index_type` ("NDVI" / "NDWI" / "NBR") | `index_raster` (Float32 GeoTIFF) |
| `raster_reclass` | **Реклассификация растра.** Изменение значений пикселей по заданным диапазонам (интервалам) для зонирования. | `numpy`, `rasterio` | `source_raster` (GeoTIFF) | `rules` (Массив маппинга: `[[0, 10, 1], [10, 30, 2]]`) | `reclassified_raster` (Int32 GeoTIFF) |
| `raster_algebra` | **Кастомная алгебра карт.** Выполнение произвольных математических выражений над матрицами нескольких растров одинакового охвата. | `rasterio`, `numpy` | `rasters` (Список растров: `{"A": "...", "B": "..."}`) | `expression` (Строка: `"(A - B) / (A + B + 0.001)"`) | `calculated_raster` (Float32 GeoTIFF) |

---

### Категория 3: Векторно-растровые преобразования и геометрия (Spatial ETL)

Инструменты для трансформации данных из одного ГИС-формата в другой и пространственной обрезки данных в оперативной памяти.

| Идентификатор (`pluginName`) | Описание алгоритма | Ключевой стек | Входные данные (Inputs) | Параметры (Parameters) | Выходные артефакты (Outputs) |
| --- | --- | --- | --- | --- | --- |
| `polygonize_raster` | **Векторизация растра.** Преобразование пиксельных кластеров в векторные полигоны (без сглаживания или со сглаживанием). | `rasterio.features`, `geopandas` | `classified_raster` (GeoTIFF) | `connectivity` (4 или 8), `mask_zero` (boolean) | `vector_polygons` (GeoJSON) |
| `rasterize_vector` | **Растеризация векторов.** "Выжигание" векторных полигонов или линий на пиксельную матрицу по выбранному атрибуту. | `rasterio.features` | `vector_features` (GeoJSON), `template_raster` (GeoTIFF для метаданных геотрансформации) | `attribute_field` (string), `default_value` (int) | `rasterized_output` (GeoTIFF) |
| `clip_raster_by_mask` | **Обрезка растра по полигону.** Маскирование тяжелого растра строго по контуру векторного полигона. | `rasterio.mask` | `source_raster` (GeoTIFF), `mask_polygon` (GeoJSON) | `crop` (boolean), `nodata_value` (int/float) | `clipped_raster` (COG-оптимизированный GeoTIFF) |
| `raster_mosaic` | **Сшивка мозаики.** Объединение нескольких смежных растровых тайлов в единое бесшовное изображение. | `rasterio.merge` | `raster_tiles` (Список путей к исходным GeoTIFF) | `resampling` ("nearest", "bilinear"), `nodata` (float) | `mosaicked_raster` (Единый COG GeoTIFF) |

---

### Категория 4: Продвинутая пространственная аналитика (Advanced Spatial Analytics)

Сложные алгоритмы пространственного анализа, агрегации данных, гидрологии и базового машинного обучения на пространственных данных.

| Идентификатор (`pluginName`) | Описание алгоритма | Ключевой стек | Входные данные (Inputs) | Параметры (Parameters) | Выходные артефакты (Outputs) |
| --- | --- | --- | --- | --- | --- |
| `zonal_statistics` | **Зональная статистика.** Расчет статистических метрик растра (минимум, максимум, среднее, сумма) в границах векторных зон. | `rasterstats`, `geopandas` | `source_raster` (GeoTIFF), `zones_vector` (GeoJSON) | `stats_list` (Массив: `["mean", "max", "std"]`) | `statistics_json` (JSON файл с массивом результатов) |
| `unsupervised_class` | **Неконтролируемая классификация.** Группировка пикселей мультиспектрального снимка на N спектральных классов методом K-Means. | `scikit-learn`, `numpy`, `rasterio` | `multiband_raster` (GeoTIFF) | `clusters_count` (int), `max_iter` (int) | `classified_raster` (Int8 GeoTIFF с индексами классов) |
| `watershed_delineation` | **Гидрологическое выделение водосборов.** Расчет направлений стоков и построение границ водосборных бассейнов по DEM. | `pysheds`, `numpy` | `dem_raster` (GeoTIFF) | `target_point_x` (float), `target_point_y` (float) | `basin_vector` (GeoJSON), `streams_raster` (GeoTIFF) |

---

### Реализация в структуре проекта

При получении задачи Оркестратор ядра выполняет роутинг на основе `pluginName`. Ниже представлен пример наполнения директории `plugins/` в репозитории воркера для поддержки данного реестра инструментов:

```text
geo-analytics-worker/
└── plugins/
    ├── __init__.py
    ├── terrain/
    │   ├── contours.py          # pluginName: generate_contours
    │   ├── slope_aspect.py      # pluginName: slope_aspect
    │   ├── viewshed.py          # pluginName: viewshed_analysis
    │   └── hillshade.py         # pluginName: hillshade
    ├── remote_sensing/
    │   ├── spectral_indices.py  # pluginName: spectral_indices
    │   ├── reclass.py           # pluginName: raster_reclass
    │   └── algebra.py           # pluginName: raster_algebra
    ├── conversion/
    │   ├── polygonize.py        # pluginName: polygonize_raster
    │   ├── rasterize.py         # pluginName: rasterize_vector
    │   ├── clip.py              # pluginName: clip_raster_by_mask
    │   └── mosaic.py            # pluginName: raster_mosaic
    └── advanced/
        ├── zonal_stats.py       # pluginName: zonal_statistics
        ├── classification.py    # pluginName: unsupervised_classification
        └── hydrology.py         # pluginName: watershed_delineation

```

Любой из этих модулей пишется независимо от других, использует выделенные под задачу ресурсы оперативной памяти в `/app/workspace/task_{UUID}/` и полностью безопасен для стабильности работы всей остальной системы.