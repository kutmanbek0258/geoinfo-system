# Архитектурная спецификация и стратегия интеграции подсистемы геоаналитики

**Проект:** Модульная интеграция тяжелой пространственной аналитики в микросервисную ГИС-платформу

**Компоненты стека:** Java (Spring Boot) / Python (GDAL, Rasterio, NumPy) / Kafka (KRaft) / PostGIS / pg_tileserv / Vue.js (OpenLayers, CesiumJS) / MinIO

---

## 1. Введение и общие принципы подсистемы

Настоящий документ определяет архитектурную стратегию внедрения тяжелой пространственной и растровой аналитики в ГИС-платформу. Основная цель — обеспечить высокую производительность вычислений и бесперебойную работу интерфейса пользователя (UI) при манипуляциях с массивными геоданными.

### Ключевые архитектурные императивы:

* **Изоляция вычислений (Sandboxing):** Все тяжелые операции (обработка матриц высот DEM, спектральный анализ растров, расчет изолиний) выносятся в выделенный асинхронный контейнер-воркер `geoanalysis-worker`. Основные Java-микросервисы API работают в неблокирующем режиме.
* **Database per Microservice:** Аналитический воркер не имеет прямого доступа к базам данных системы. Обмен данными происходит исключительно асинхронно через брокер сообщений Kafka и объектное хранилище MinIO по S3 API.
* **Принцип «Ядро и Плагины»:** Расширение аналитического функционала происходит путем добавления изолированных модулей (плагинов) в контейнер воркера без изменения логики инфраструктурной обвязки (оркестратора).
* **Концепция «Временного слоя» (Staging Layer):** Любой сгенерированный аналитический результат изначально публикуется как временный (промежуточный) слой. Пользователь имеет возможность визуализировать, фильтровать и анализировать результат в браузере на максимальной скорости (60 FPS) перед принятием решения о сохранении (Commit) или сбросе (Rollback) данных.

---

## 2. Архитектура аналитического воркера (`geoanalysis-worker`)

Воркер развертывается как автономное приложение на базе Docker-образа, содержащего оптимизированные C++ библиотеки ГИС-ядра.

### 2.1. Структура проекта контейнера

```text
geo-analytics-worker/
├── Dockerfile                  # Базовый образ на базе ghcr.io/osgeo/gdal
├── requirements.txt            # Инфраструктурные и ГИС-зависимости Python
├── orchestrator/               # Диспетчер ядра (управление жизненным циклом)
│   ├── __init__.py
│   ├── main.py                 # Точка входа, Kafka-consumer loop
│   ├── s3_client.py            # I/O операции с MinIO (S3 SDK)
│   ├── kafka_client.py         # Продюсер и консьюмер (confluent-kafka)
│   └── plugin_manager.py       # Реестр и динамический импорт модулей
├── core/
│   └── base_plugin.py          # Абстрактный интерфейс BaseGeoPlugin
└── plugins/                    # Изолированные функциональные плагины
    ├── __init__.py
    ├── terrain/                # Анализ рельефа (contours, viewshed, slope)
    ├── remote_sensing/         # Дистанционное зондирование (NDVI, классификация)
    └── conversion/             # Растрово-векторные конвертации (polygonize)

```

### 2.2. Базовая конфигурация окружения (Docker Compose)

Для обеспечения I/O-интенсивных операций GDAL в оперативной памяти применяется монтирование RAM-диска (`tmpfs`).

```yaml
  geo-analysis-worker:
    build: ./geo-analytics-worker
    restart: always
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - MINIO_ENDPOINT=minio:9000
      - MINIO_ACCESS_KEY=${MINIO_ROOT_USER}
      - MINIO_SECRET_KEY=${MINIO_ROOT_PASSWORD}
      - WORKSPACE_DIR=/app/workspace
    tmpfs:
      - /app/workspace:size=4G,mode=1777  # Выделенный RAM-диск под кэш GDAL и файлы
    deploy:
      resources:
        limits:
          memory: 6G                       # 4G под tmpfs + 2G под процессы Python

```

---

## 3. Стратегия управления памятью и жизненный цикл задачи

Использование `tmpfs` накладывает жесткие требования к стабильности операций и очистке памяти для исключения утечек (OOM).

### Пошаговый пайплайн обработки задачи:

```
[Kafka Request] -> [Оркестратор ядра] 
                          │
                          ▼
             [Создание папки в tmpfs] ──► /app/workspace/task_{UUID}/
                          │
                          ▼
            [Скачивание исходников из S3]
                          │
                          ▼
              [Запуск плагина аналитики] ──► Кэш GDAL перенаправлен в tmpfs
                          │
                          ▼
            [Выгрузка результатов в S3]
                          │
                          ▼
             [Отправка ответа в Kafka]
                          │
                          ▼
             [Жесткий Сборщик Мусора] ──► shutil.rmtree() папки задачи

```

1. **Аллокация:** При получении задачи из топика `geoanalysis.tasks` Оркестратор генерирует изолированную рабочую директорию `/app/workspace/task_{task_id}/` на RAM-диске.
2. **GDAL Sandboxing:** Оркестратор принудительно перенаправляет временные файлы кэша CPL во временную директорию задачи:
```python
gdal.SetConfigOption('CPL_TMPDIR', local_workspace_path)

```


3. **Teardown (Сборка мусора):** Блок обработки задачи оборачивается в конструкцию `try...finally`. Независимо от исхода вычислений (успех или критическая ошибка алгоритма), Оркестратор выполняет рекурсивное удаление локальной директории:
```python
shutil.rmtree(local_workspace_path, ignore_errors=True)

```



---

## 4. Стандартизация обмена данными (Схемы контрактов API)

Взаимодействие между основным бэкендом и аналитическим воркером полностью асинхронно. Передача бинарных данных через шину сообщений запрещена.

### 4.1. Входящий контракт (Топик: `geoanalysis.tasks`)

```json
{
  "taskId": "a1b2c3d4-5678-90ef-1234-567890abcdef",
  "pluginName": "zonal_statistics",
  "inputs": {
    "raster_dem": "s3://gis-data/raw/dem_layer.tif",
    "vector_zones": "s3://gis-data/vectors/fields_contour.geojson"
  },
  "parameters": {
    "stats": ["mean", "max", "min"],
    "nodata": -9999
  }
}

```

### 4.2. Исходящий контракт (Топик: `geoanalysis.results`)

```json
{
  "taskId": "a1b2c3d4-5678-90ef-1234-567890abcdef",
  "status": "COMPLETED", 
  "outputs": {
    "result_json": "s3://gis-data/temp/stats_a1b2c3.json",
    "processed_raster": "s3://gis-data/temp/render_a1b2c3.tif"
  },
  "error": null,
  "metrics": {
    "executionTimeMs": 1450
  }
}

```

*Примечание: При статусе `FAILED` поле `outputs` передается пустым, а поле `error` содержит детальный traceback ошибки для логирования.*

---

## 5. Концепция временных слоев (Staging) и рендеринг тяжелых данных

Отображение результатов аналитики объемом более 10 МБ в формате GeoJSON на фронтенде блокирует поток интерфейса. Для решения этой проблемы подсистема разделяет рендеринг результатов на векторный и растровый потоки.

### 5.1. Пайплайн обработки Векторных результатов (Динамический MVT)

Вместо прямой передачи векторов в браузер, данные кэшируются в буферной таблице СУБД PostGIS и нарезаются на лету с помощью `pg_tileserv`.

```
[Воркер] ──> GeoJSON в S3 ──> [Java Geodata-Service]
                                      │
                                      ▼ (Batch Insert)
                        PostGIS: [temp_analysis_geometries]
                                      │
                                      ▼ (Запрос тайла RPC)
                                [pg_tileserv]
                                      │
                                      ▼ (Протокол MVT .pbf)
                        Vue.js: [OpenLayers / Cesium]

```

1. **Буферное хранилище PostGIS:** Микросервис геоданных (`geodata-service`) управляет единой изолированной таблицей для хранения временных результатов:
```sql
CREATE TABLE public.temp_analysis_geometries (
    id BIGSERIAL PRIMARY KEY,
    task_id UUID NOT NULL,
    geom geometry(Geometry, 4326) NOT NULL,
    properties jsonb DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_temp_geom_task_id ON public.temp_analysis_geometries(task_id);
CREATE INDEX idx_temp_geom_gist ON public.temp_analysis_geometries USING gist(geom);

```


2. **Динамическая функция нарезки тайлов (RPC):** В схеме базы данных регистрируется функция, доступная для автоматической публикации через `pg_tileserv`:
```sql
CREATE OR REPLACE FUNCTION public.get_staging_layer(z integer, x integer, y integer, task_uuid text)
RETURNS bytea AS $$
DECLARE
    mvt bytea;
BEGIN
    SELECT ST_AsMVT(mvt_geom, 'staging_layer') INTO mvt FROM (
        SELECT ST_AsMVTGeom(ST_Transform(t.geom, 3857), ST_TileEnvelope(z, x, y), extent => 4096, buffer => 64) AS mvt_geom, t.properties
        FROM public.temp_analysis_geometries t
        WHERE t.task_id = task_uuid::uuid 
          AND t.geom && ST_Transform(ST_TileEnvelope(z, x, y), 4326)
    ) AS mvt_geom;
    RETURN mvt;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

```


3. **Инициализация на фронтенде:** При получении события об успешном завершении задачи через WebSocket, Vue.js приложение не скачивает файл, а монтирует слой векторных тайлов OpenLayers:
```javascript
const stagingLayer = new VectorTileLayer({
    source: new VectorTileSource({
        format: new MVT(),
        url: `https://gis-domain.com/tileserv/rpc/get_staging_layer/{z}/{x}/{y}.pbf?task_uuid=${taskId}`
    })
});

```



### 5.2. Пайплайн обработки Растровых результатов (Стриминг COG)

Если результатом аналитики является растр (например, зоны уклонов или индекс вегетации), воркер компилирует его как оптимизированный для облака GeoTIFF (**COG**).

1. **Стриминг по требованию (HTTP Range Requests):** Файл сохраняется во временный бакет MinIO. Микросервис генерирует временную ссылку (Pre-signed URL).
2. **WebGL-рендеринг на клиенте:** Фронтенд с помощью библиотеки OpenLayers инициализирует `ol/source/GeoTIFF`. Браузер выкачивает из S3 исключительно те пиксели и уровни пирамиды растра, которые необходимы для текущего масштаба и охвата экрана. Нагрузка на память клиента минимальна.

---

## 6. Жизненный цикл временного слоя и механизмы очистки

Для исключения неконтролируемого роста объемов хранилищ данных (базы данных PostGIS и бакетов S3) внедряется жесткая схема управления состояниями (State Machine).

### 6.1. Действие: Фиксация (Commit)

Если результаты анализа удовлетворяют пользователя, в интерфейсе нажимается кнопка **«Сохранить»**.

* Фронтенд отправляет запрос `POST /api/analysis/{taskId}/commit`.
* `geodata-service` в рамках единой транзакции переносит данные из временной таблицы в постоянные таблицы слоев ГИС:
```sql
INSERT INTO public.production_layer_geometries (geom, properties, layer_id)
SELECT geom, properties, :targetLayerId 
FROM public.temp_analysis_geometries 
WHERE task_id = :taskId;

DELETE FROM public.temp_analysis_geometries WHERE task_id = :taskId;

```


* Временные файлы удаляются из S3-бакета.

### 6.2. Действие: Сброс (Rollback) или Автоочистка (TTL)

Если пользователь повторяет анализ с новыми параметрами или закрывает вкладку браузера:

* Данные остаются во временном статусе.
* **В базе данных (PostGIS):** В `geodata-service` запускается планировщик задач Spring Boot (`@Scheduled`), который каждые 60 минут удаляет устаревшие временные слои:
```sql
DELETE FROM public.temp_analysis_geometries WHERE created_at < NOW() - INTERVAL '2 hours';

```


* **В объектном хранилище (MinIO):** На бакет `temp/` устанавливается стандартная политика жизненного цикла (Lifecycle Policy) конфигурации S3, автоматически уничтожающая файлы со временем создания (TTL) более 24 часов.

---

## 7. Стандарт разработки функционального модуля (Плагина)

Для добавления нового аналитического инструмента разработчик пишет изолированный Python-класс в папке `plugins/`. Вся логика работы с сетью, дисками, Kafka и MinIO абстрагирована оркестратором.

### Базовый интерфейс плагина (`core/base_plugin.py`):

```python
from abc import ABC, abstractmethod
from typing import Dict, Any

class BaseGeoPlugin(ABC):
    @property
    @abstractmethod
    def plugin_name(self) -> str:
        """
        Уникальное имя плагина, строго соответствующее
        полю pluginName во входящем сообщении Kafka.
        """
        pass

    @abstractmethod
    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        """
        Выполнение ГИС-алгоритма в изолированном пространстве tmpfs.
        :param local_inputs: Маппинг входных ключей к локальным путям файлов на RAM-диске.
        :param params: Пользовательские бизнес-параметры.
        :param workspace: Путь к изолированной временной папке для записи результатов.
        :return: Маппинг выходных ключей к локальным путям созданных файлов.
        """
        pass

```

### Чек-лист соответствия для новых плагинов:

1. **Чистота вычислений:** Запрещены любые внешние сетевые вызовы (HTTP, базы данных). Данные поступают только через `local_inputs`.
2. **Безопасность потоков:** Использование глобальных переменных внутри плагина запрещено, так как воркер обрабатывает задачи параллельно.
3. **Ограничение ресурсов:** Операции с массивами NumPy должны использовать векторизованные вычисления для исключения утечек CPU в циклах Python.
4. **Контроль за метаданными:** Любой создаваемый растровый или векторный файл на выходе обязан сохранять исходную пространственную привязку (в базовом сценарии EPSG:4326 или EPSG:3857) и корректно прописывать флаг NoData.