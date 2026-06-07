# План реализации импорта Shapefile в GeoInfoSystem

Этот документ описывает стратегию реализации асинхронного импорта векторных данных из Shapefile через микросервисную архитектуру с использованием GDAL и Kafka.

## 1. Архитектура решения

Процесс разделен между четырьмя компонентами:
1.  **geoabstraction-service**: Оркестратор задач, управляет статусами и жизненным циклом файлов.
2.  **geoabstract-worker (Python)**: Выполняет тяжелую операцию конвертации Shapefile -> GeoJSON с помощью GDAL (`ogr2ogr`).
3.  **geodata-service**: Выполняет разбор GeoJSON и сохранение объектов в PostGIS.
4.  **MinIO**: Используется как промежуточное хранилище для архивов и GeoJSON файлов.

## 2. Сценарий работы (Workflow)

1.  **Инициация**: Frontend запрашивает импорт, указывая `projectId` и `folderId`.
2.  **Подготовка**: `geoabstraction-service` создает задачу `SHAPEFILE_TO_GEOJSON` и возвращает Presigned URL.
3.  **Загрузка**: Клиент загружает `.zip` архив в MinIO и подтверждает загрузку.
4.  **Конвертация**: `geoabstract-worker` скачивает ZIP, запускает `ogr2ogr` (WGS84, UTF-8), загружает GeoJSON в MinIO и бросает событие `PROCESSED`.
5.  **Импорт в БД**: `geodata-service` получает событие, скачивает GeoJSON, распределяет объекты по таблицам (Points, Lines, Polygons) и бросает событие `INGESTED`.
6.  **Завершение**: `geoabstraction-service` переводит задачу в `READY`, инициирует удаление временных файлов через воркер.

## 3. Технические детали

### GDAL Команда (Воркер):
```bash
ogr2ogr -f "GeoJSON" output.json /vsizip/input.zip -t_srs EPSG:4326 -lco COORDINATE_PRECISION=7 --config SHAPE_ENCODING UTF-8
```

### Ingestion (geodata-service):
- Использование Jackson Streaming API для работы с большими файлами.
- Маппинг всех атрибутов Shapefile в колонку `characteristics` (JSONB).
- Поддержка Z-координат (автоматическое приведение к 3D).

## 4. Статусы задачи
- `NEW`: Задача создана, ожидается загрузка.
- `QUEUED`: В очереди на конвертацию.
- `PROCESSING`: Выполняется GDAL.
- `PROCESSED`: GeoJSON готов в MinIO.
- `INGESTING`: Идет запись в PostGIS.
- `INGESTED`: Данные в БД.
- `READY`: Успешное завершение, файлы удалены.
- `FAILED`: Ошибка на любом этапе.

## 5. Этапы реализации

- [x] **Phase 1**: Обновление Common Lib и контрактов Kafka.
- [x] **Phase 2**: Реализация `VectorProcessor` в Python воркере.
- [x] **Phase 3**: Реализация `VectorIngestionService` в `geodata-service`.
- [x] **Phase 4**: Реализация оркестрации и API в `geoabstraction-service`.
- [ ] **Phase 5**: Реализация UI во фронтенде (Диалог импорта).
- [ ] **Phase 6**: Обновление QGIS плагина.
- [ ] **Phase 7**: Тестирование и отладка.
