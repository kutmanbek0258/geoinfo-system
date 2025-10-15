# Техническое задание: ГеоИнфоСистема с интеграцией DSM и Raster Analysis Service

## 1. Введение
Система расширяется за счёт интеграции цифровых моделей поверхности (DSM) для выполнения пространственных расчётов с учётом рельефа. Основная цель — обеспечить возможность вычисления длины линий и площади полигонов с использованием данных рельефа, получаемых из DSM (Digital Surface Model).

## 2. Общая архитектура
Система сохраняет микросервисную архитектуру и принцип **Database per Service**. Все сервисы построены на **Java Spring Boot** с взаимодействием через **Feign** и **Kafka**.

### Новый сервис
**raster-analysis-service** — микросервис для хранения и анализа DSM-данных, рельефных вычислений и работы с растровыми моделями. Он является единственной точкой для загрузки, хранения и анализа данных DSM.

## 3. Потоки данных и взаимодействие

### Загрузка DSM напрямую в raster-analysis-service
- Пользователь загружает DSM-файл напрямую в `raster-analysis-service` через REST API `/dsm/upload`.
- raster-analysis-service валидирует файл (проверка формата GeoTIFF, координатной системы, разрешения и метаданных).
- Выполняется импорт данных DSM в PostGIS (разбиение на тайлы, запись в таблицы `raster_metadata` и `raster_tiles`).
- После успешного импорта исходный GeoTIFF **удаляется** с локального хранилища, оставляя только данные в базе.
- raster-analysis-service публикует событие `dsm.imported` в Kafka, уведомляя другие сервисы о доступности нового DSM.

Таким образом, raster-analysis-service является самостоятельной точкой загрузки и обработки DSM. document-service используется исключительно для других типов документов.

## 4. Хранилище данных

### PostGIS (БД raster-analysis-service)
- Хранит DSM-растр (в виде raster-тайлов).
- Основные таблицы:
  - `raster_metadata (id, name, resolution, crs, bounds, import_date)`
  - `raster_tiles (id, raster_id, rast)` — данные растра (тип `raster`).

Импорт выполняется с помощью GeoTools и PostGIS Raster API.

## 5. API raster-analysis-service

### Основные эндпоинты
- `POST /dsm/upload` — загрузка GeoTIFF DSM.
  - Параметры: файл GeoTIFF.
  - Действие: импорт в PostGIS, удаление временного файла.
  - Ответ: `{id, name, resolution, bounds}`.

- `POST /analysis/length` — вычисление длины MultiLineString с учётом рельефа.
  - Вход: GeoJSON MultiLineString, `dsmId`, тип расчёта (`projected` | `terrain`).
  - Выход: `{length3D, length2D, elevationStats}`.

- `POST /analysis/area` — вычисление площади полигона с учётом рельефа.
  - Вход: GeoJSON Polygon, `dsmId`.
  - Выход: `{area3D, area2D, slopeAverage}`.

- `GET /dsm/{id}/profile` — возврат профиля высот вдоль линии.
  - Вход: GeoJSON LineString.
  - Выход: массив `{distance, elevation}`.

### Feign-взаимодействие
`geodata-service` вызывает Feign-запросы в `raster-analysis-service` для получения рельефных вычислений. Пример:
```java
@FeignClient(name = "raster-analysis-service")
public interface RasterAnalysisClient {
    @PostMapping("/analysis/length")
    LengthResponse calculateLength(@RequestBody LengthRequest request);
}
```

## 6. Алгоритмы расчётов

### 6.1 Длина с учётом рельефа
- Геометрия линии дискретизируется (sampling).
- Для каждой точки определяется высота из DSM.
- Рассчитывается трёхмерное расстояние между последовательными точками.
- Итоговая длина: сумма всех 3D-сегментов.

### 6.2 Площадь полигона с учётом рельефа
- Полигон делится на сетку.
- Для каждой ячейки вычисляется локальный уклон (градиент высоты).
- Площадь корректируется по формуле `area3D = Σ(cell_area * sqrt(1 + grad^2))`.

## 7. Производительность и требования
- Импорт 1GB GeoTIFF ≤ 2 мин.
- Расчёт длины MultiLineString ≤ 2 сек для 1000 вершин.
- Возможность параллельного импорта нескольких DSM.
- Асинхронная обработка с Kafka и Spring @Async.

## 8. Мониторинг и тестирование
- Prometheus/Grafana для метрик импорта и анализа.
- Тесты: JUnit + Testcontainers (PostGIS, Kafka).
- Генерация синтетических DSM для нагрузочных тестов.

## 9. Развёртывание
- Dockerfile + docker-compose (PostGIS, Kafka, raster-analysis-service).
- Переменные окружения для путей временных файлов DSM и лимита размера.

## 10. План реализации
**Этап 1:** API загрузки DSM и импорт в PostGIS.  
**Этап 2:** Реализация расчётов длины и площади.  
**Этап 3:** Интеграция с geodata-service.  
**Этап 4:** Мониторинг, тестирование и документация.