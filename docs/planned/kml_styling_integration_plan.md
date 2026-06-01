# План внедрения KML-совместимых стилей для геообъектов

## 1. Описание задачи
Целью является обеспечение сохранения и отображения стилей (цвета, толщина линий, иконки), импортированных из KML-файлов или настроенных вручную, для всех типов геообъектов (точки, линии, полигоны). Стиль должен храниться в гибком формате JSONB внутри поля `characteristics`.

## 2. Модификация базы данных (geodata-service)

### 2.1. Добавление поля characteristics
Для обеспечения единообразия и гибкости необходимо добавить колонку `characteristics` (JSONB) в таблицы линий и полигонов (в таблице точек она уже есть).

- **Таблица `project_multilines`**: Добавить `characteristics JSONB DEFAULT '{}'`.
- **Таблица `project_polygons`**: Добавить `characteristics JSONB DEFAULT '{}'`.

### 2.2. Liquibase миграция
Создать новый файл миграции `geodata-service/database/release-1.0.0/object/add_characteristics_to_all_geo_objects.sql` и включить его в `release.changelog.yaml`.

## 3. Обновление серверной части (geodata-service)

### 3.1. Обновление JPA сущностей
Обновить классы `ProjectPoint`, `ProjectMultiline` и `ProjectPolygon`, обеспечив наличие поля:
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private Map<String, Object> characteristics;
```
*(Примечание: В `ProjectPoint` поле уже есть, необходимо добавить в остальные).*

### 3.2. Обновление DTO
Обновить DTO для всех типов объектов (`ProjectPointDto`, `ProjectMultilineDto`, `ProjectPolygonDto` и их Create/Update версии), добавив поле `characteristics`.

### 3.3. Доработка KML-импорта (`KmlImportServiceImpl`)
- **Парсинг стилей**: Реализовать сбор глобальных стилей (`<Style>`) и карт стилей (`<StyleMap>`) в начале процесса импорта.
- **Маппинг стилей в Placemark**: Для каждого `<Placemark>` извлекать `<styleUrl>` и находить соответствующий стиль.
- **Конвертация в JSON**: Преобразовывать атрибуты KML (цвет `aabbggrr`, толщина `width`, параметры иконки) в структуру внутри `characteristics.style`.

#### 3.3.1. Обработка иконок (ProjectPoint)
Если в стиле точки обнаружен `<Icon>`, система должна:
1.  **Загрузить иконку**: Если `href` указывает на внешний URL, `geodata-service` загружает контент иконки.
2.  **Сохранить в Minio**: Отправить файл в `document-service` через Feign-клиент.
3.  **Привязать ID**: Сохранить полученный `documentId` или публичный URL в `characteristics.style.icon.url`.

Пример структуры JSON для точки:
```json
{
  "style": {
    "icon": {
      "url": "/api/documents/public/image/{uuid}",
      "scale": 1.1,
      "heading": 0,
      "hotSpot": {"x": 0.5, "y": 0.5, "xunits": "fraction", "yunits": "fraction"}
    },
    "label": {
      "color": "#ffffffff",
      "scale": 1.0
    }
  }
}
```

## 4. Интеграция между сервисами

### 4.1. Feign-клиент для document-service
В `geodata-service` создать интерфейс для взаимодействия с `document-service`, позволяющий программно загружать файлы (иконки).

### 4.2. Публичный доступ к иконкам
Использовать эндпоинт `GET /api/documents/public/image/{documentId}` в `document-service` для отображения иконок на карте без необходимости авторизации для каждого тайла/объекта (если это допустимо политикой безопасности).

## 5. Синхронизация данных (Kafka & Search Service)

### 5.1. Kafka события
Поля `characteristics` (включая `style`) передаются в полезной нагрузке событий Kafka для обеспечения консистентности данных между микросервисами.

### 5.2. Исключение стилей из индексации (Search Service)
Стили используются только для визуализации на карте и не должны участвовать в поиске. Необходимо:
1.  **В `search-service`**: При получении события из Kafka, перед сохранением в Elasticsearch, удалять вложенный объект `style` из мапы `characteristics`.
2.  **Альтернатива**: Настроить маппинг в Elasticsearch для индекса `geo_objects` так, чтобы поле `characteristics.style` имело тип `"enabled": false`, что позволит хранить данные в `_source`, но не индексировать их для поиска. (Предпочтителен вариант с удалением перед индексацией для экономии места в индексе).

## 6. Визуализация (Frontend)

### 6.1. OpenLayers Styling
Обновить логику отрисовки слоев на фронтенде:
- При получении геообъектов проверять наличие `characteristics.style`.
- Динамически создавать стили OpenLayers (`ol/style/Style`) на основе данных из JSON.
- Реализовать парсер KML-цветов (`aabbggrr`) в формат, понятный браузеру (`rgba` или `#rrggbbaa`).

## 7. Этапы реализации
1. **Этап 1**: Миграция БД и обновление моделей в `geodata-service`.
2. **Этап 2**: Реализация парсинга `<Style>` и `<styleUrl>` в `KmlImportServiceImpl`.
3. **Этап 3**: Настройка интеграции с `document-service` для иконок.
4. **Этап 4**: Настройка `search-service` для исключения стилей из индексации.
5. **Этап 5**: Доработка фронтенда для визуализации стилей.
