# 📋 Стратегия перехода к Schema-Driven UI для модулей геоаналитики (на базе JSON Schema Form)

В документе представлена переработанная архитектурная стратегия внедрения динамических форм для запуска 15 плагинов геоанализа. Стратегия основана на детальном анализе исходного кода воркера Python (`geoanalysis-worker`), бэкенда (`geoabstraction-service`) и Vuetify 3 фронтенда (`frontend`).

---

## 1. Результаты анализа текущей кодовой базы

### 1.1. Python воркер (`geoanalysis-worker`)
*   Все плагины (например, [watershed_delineation.py](file:///C:/Users/admin/Documents/dev/geoinfo-system/geoanalysis-worker/app/plugins/watershed_delineation.py)) наследуются от `GeoWorkerPlugin` и реализуют логику в методе `run(local_inputs, params, workspace)`.
*   `local_inputs` — это словарь путей к локальным файлам, скачанным из S3. Ключи словаря соответствуют именам входов (например, `dem_raster`, `source_raster`).
*   `params` — это словарь параметров, передаваемый напрямую в алгоритмы (например, `threshold` для русла рек или `clusters_count` для K-Means).

### 1.2. Бэкенд (`geoabstraction-service`)
*   Создание задач выполняется эндпоинтом `POST /api/analysis/tasks` в классе [AnalysisTaskController.java](file:///C:/Users/admin/Documents/dev/geoinfo-system/geoabstraction-service/src/main/java/kg/geoinfo/system/geoabstraction/controller/AnalysisTaskController.java).
*   Тело запроса десериализуется в класс [CreateAnalysisTaskDto.java](file:///C:/Users/admin/Documents/dev/geoinfo-system/geoabstraction-service/src/main/java/kg/geoinfo/system/geoabstraction/dto/CreateAnalysisTaskDto.java):
    *   `pluginName` (String)
    *   `projectId` (UUID)
    *   `inputs` (`Map<String, AnalysisDataSource>`) — здесь описываются пространственные слои. `AnalysisDataSource` поддерживает ссылки на снимки, рельеф, векторы и результаты предыдущих задач (`PREVIOUS_TASK_RESULT`).
    *   `parameters` (`Map<String, Object>`) — плоский или вложенный JSON-объект параметров алгоритма.

### 1.3. Фронтенд (`frontend`)
*   Сейчас для каждого плагина написано отдельное диалоговое окно (всего 15 окон, например [SlopeDialog.vue](file:///C:/Users/admin/Documents/dev/geoinfo-system/frontend/src/components/map/shared/SlopeDialog.vue), [UnsupervisedClassDialog.vue](file:///C:/Users/admin/Documents/dev/geoinfo-system/frontend/src/components/map/shared/UnsupervisedClassDialog.vue)).
*   Все они используют фреймворк **Vuetify 3** (`v-dialog`, `v-card`, `v-select`, `v-text-field`, `v-switch`).
*   Формируют идентичный контракт `CreateAnalysisTaskDto` и запускают задачи через экшен Vuex `geodata/triggerAnalysis`.

---

## 2. Архитектура на базе JSON Schema Form

Для генерации форм мы выберем подход **Vuetify-совместимой JSON-схемы**.

### Варианты генератора:
1.  **Использование библиотеки (например, `@lljj/vue3-form-vuetify`):** Генератор форм на базе JSON Schema для Vue 3 и Vuetify.
2.  **Собственный Vuetify-генератор форм (`DynamicSchemaForm.vue`):** Рекомендуется, так как в ГИС-системе требуются кастомные ГИС-виджеты:
    *   Интеграция с картой для клика и получения координат (например, *Viewshed*, *Watershed*).
    *   Сложное динамическое объединение слоев из Vuex (снимки, рельеф, задачи `COMPLETED`).
    *   Специфический матричный ввод диапазонов реклассификации (`rules_array`).
    *   Автоматическая подгрузка и валидация через Vuetify `rules`.

---

## 3. Спецификация JSON-схемы ГИС-плагина

Каждый плагин декларирует единую JSON-схему в формате **JSON Schema Draft-07** с использованием кастомных форматов/свойств (`format` или `ui:widget`) для ГИС-полей.

### 3.1. Описание пространственных входов (`inputs` как `properties`)
Для пространственных слоев используется тип `string` со следующими форматами:
*   `"format": "raster-layer"` — выбор растрового слоя (снимки `imageryLayers` + рельеф `terrainLayers` + результаты прошлых задач с растром).
*   `"format": "vector-layer"` — выбор векторного слоя (векторы `folders` + результаты задач с векторным выводом).
*   `"format": "terrain-layer"` — только слои рельефа (DEM).
*   `"format": "map-point"` — точка на карте. Рендерит текстовое поле с кнопкой «Указать на карте» (активирует клик по карте в OpenLayers/Cesium).

### 3.2. Описание параметров (`parameters` как `properties`)
*   Числа (`type: "number"`, `type: "integer"`, `minimum`, `maximum`).
*   Булевы флаги (`type: "boolean"`).
*   Выбор из списка (`type: "string"`, `enum: [...]`).
*   Кастомный матричный ввод реклассификации:
    ```json
    {
      "type": "array",
      "items": {
        "type": "array",
        "items": { "type": "number" },
        "minItems": 3,
        "maxItems": 3
      },
      "ui:widget": "reclass_rules"
    }
    ```

---

## 4. Конкретные примеры схем для плагинов

### 4.1. Плагин «Крутизна уклонов» (Простая схема)
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Расчет крутизны уклонов",
  "pluginName": "slope",
  "icon": "mdi-slope-uphill",
  "type": "object",
  "properties": {
    "inputs": {
      "type": "object",
      "properties": {
        "dem_file": {
          "type": "string",
          "format": "terrain-layer",
          "title": "Источник рельефа (DEM)"
        }
      },
      "required": ["dem_file"]
    },
    "parameters": {
      "type": "object",
      "properties": {
        "units": {
          "type": "string",
          "title": "Единицы измерения уклона",
          "enum": ["degrees", "percent"],
          "enumTitles": ["Градусы (0-90°)", "Проценты (%)"],
          "default": "degrees"
        }
      },
      "required": ["units"]
    }
  }
}
```

### 4.2. Плагин «Выделение водосборов» (Сложные входы + Клик по карте)
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Выделение водосборов",
  "pluginName": "watershed_delineation",
  "icon": "mdi-water-percent",
  "type": "object",
  "properties": {
    "inputs": {
      "type": "object",
      "properties": {
        "dem_raster": {
          "type": "string",
          "format": "terrain-layer",
          "title": "Исходный растр высот (DEM)"
        }
      },
      "required": ["dem_raster"]
    },
    "parameters": {
      "type": "object",
      "properties": {
        "target_point": {
          "type": "object",
          "title": "Устьевая точка водосбора (Замыкающий створ)",
          "format": "map-point",
          "properties": {
            "x": { "type": "number", "title": "Долгота (X)" },
            "y": { "type": "number", "title": "Широта (Y)" }
          },
          "required": ["x", "y"]
        },
        "threshold": {
          "type": "integer",
          "title": "Порог накопления стока (Аккумуляция)",
          "minimum": 100,
          "maximum": 50000,
          "default": 1000
        }
      },
      "required": ["target_point", "threshold"]
    }
  }
}
```

### 4.3. Плагин «Реклассификация растра» (Кастомная матрица)
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Реклассификация растра",
  "pluginName": "raster_reclass",
  "icon": "mdi-palette-swatch-outline",
  "type": "object",
  "properties": {
    "inputs": {
      "type": "object",
      "properties": {
        "source_raster": {
          "type": "string",
          "format": "raster-layer",
          "title": "Исходный растровый слой"
        }
      },
      "required": ["source_raster"]
    },
    "parameters": {
      "type": "object",
      "properties": {
        "default_value": {
          "type": "number",
          "title": "Значение по умолчанию (вне диапазонов)",
          "default": 0
        },
        "rules": {
          "type": "array",
          "title": "Правила реклассификации (Диапазоны [Min, Max))",
          "ui:widget": "reclass_rules",
          "items": {
            "type": "array",
            "items": { "type": "number" },
            "minItems": 3,
            "maxItems": 3
          },
          "default": [[0, 100, 1]]
        }
      },
      "required": ["default_value", "rules"]
    }
  }
}
```

---

## 5. Изменения в бэкенде (`geoanalysis-worker` & `geoabstraction-service`)

### 5.1. Декларация схем в Python-воркере
В `GeoWorkerPlugin` объявляется новый метод получения схемы. Каждый плагин хранит схему в JSON-файле рядом с кодом и считывает её при вызове:
```python
import json
import os

class SlopePlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "slope"

    def get_schema(self) -> dict:
        schema_path = os.path.join(os.path.dirname(__file__), "slope.schema.json")
        with open(schema_path, "r", encoding="utf-8") as f:
            return json.load(f)
```

### 5.2. Агрегация и раздача в Spring Boot
*   При старте воркер отправляет свои схемы в топик конфигурации Kafka или предоставляет REST-метод (если поддерживается связь).
*   В качестве более надежной альтернативы: все `.json` файлы схем копируются в ресурсы Spring Boot `geoabstraction-service` при сборке проекта.
*   Реализуется эндпоинт `GET /api/analysis/plugins` (возвращает список всех доступных плагинов со схемами) и `GET /api/analysis/plugins/{name}/schema` (схема конкретного плагина).

---

## 6. Реализация динамической формы во фронтенде

### 6.1. Компонент `DynamicSchemaForm.vue`
Этот компонент берет на себя рендеринг полей. Ниже приведена концептуальная структура компонента:

```html
<template>
  <v-form ref="form" v-model="isValid">
    <!-- Рендеринг пространственных входов -->
    <div class="text-subtitle-1 mb-2">Пространственные данные:</div>
    <div v-for="(inputSchema, inputKey) in schema.properties.inputs.properties" :key="inputKey">
      <v-select
        v-model="inputs[inputKey]"
        :items="getLayerOptions(inputSchema.format)"
        :label="inputSchema.title"
        variant="outlined"
        density="comfortable"
        :rules="[v => !!v || 'Обязательно']"
        required
      ></v-select>
    </div>

    <v-divider class="my-4"></v-divider>

    <!-- Рендеринг параметров алгоритма -->
    <div class="text-subtitle-1 mb-2">Параметры:</div>
    <div v-for="(paramSchema, paramKey) in schema.properties.parameters.properties" :key="paramKey">
      
      <!-- Чекбокс / Переключатель (Boolean) -->
      <v-switch
        v-if="paramSchema.type === 'boolean'"
        v-model="parameters[paramKey]"
        :label="paramSchema.title"
        color="primary"
        density="comfortable"
      ></v-switch>

      <!-- Выпадающий список (Enum / Select) -->
      <v-select
        v-else-if="paramSchema.enum"
        v-model="parameters[paramKey]"
        :items="getEnumItems(paramSchema)"
        :label="paramSchema.title"
        variant="outlined"
        density="comfortable"
        :rules="[v => !!v || 'Обязательно']"
      ></v-select>

      <!-- Матрица правил реклассификации -->
      <div v-else-if="paramSchema['ui:widget'] === 'reclass_rules'">
        <v-label class="mb-2">{{ paramSchema.title }}</v-label>
        <rules-matrix-editor v-model="parameters[paramKey]"></rules-matrix-editor>
      </div>

      <!-- Выбор точки на карте -->
      <div v-else-if="paramSchema.format === 'map-point'">
        <map-point-picker
          v-model="parameters[paramKey]"
          :title="paramSchema.title"
        ></map-point-picker>
      </div>

      <!-- Стандартное числовое/строковое поле -->
      <v-text-field
        v-else
        v-model="parameters[paramKey]"
        :label="paramSchema.title"
        :type="paramSchema.type === 'integer' || paramSchema.type === 'number' ? 'number' : 'text'"
        variant="outlined"
        density="comfortable"
        :rules="getValidationRules(paramSchema)"
      ></v-text-field>
      
    </div>
  </v-form>
</template>
```

### 6.2. Универсальный ГИС-селектор слоев (`getLayerOptions`):
Фронтенд автоматически фильтрует данные из Vuex в зависимости от `format` в схеме:
1.  `terrain-layer`: Возвращает только `terrainLayers` (READY) + завершенные задачи с плагинами `slope`, `aspect`, `hillshade`, `watershed_delineation` (растр streams).
2.  `raster-layer`: Возвращает снимки `imageryLayers` + слои рельефа + растровые выводы всех завершенных задач.
3.  `vector-layer`: Возвращает векторные папки `folders` + векторные выводы завершенных задач (например, `basin.geojson` из водосборов или изолинии).

---

## 7. Пошаговый план миграции на Schema-Driven UI

### Шаг 1. Интеграция схем в Python-воркере
*   Создать абстрактный метод `get_schema()` в `GeoWorkerPlugin`.
*   Написать схемы в формате `.schema.json` для первых 3 пилотных плагинов: `slope`, `aspect`, `hillshade`.
*   Реализовать их загрузку и возврат.

### Шаг 2. Разработка REST API в Spring Boot
*   Написать эндпоинт в `AnalysisTaskController` для раздачи схем плагинов.
*   Настроить кэширование схем при инициализации приложения для высокой производительности.

### Шаг 3. Разработка компонентов Dynamic UI
*   Реализовать универсальный виджет выбора точек на карте `MapPointPicker.vue` (интегрируемый с общим синглтоном карты).
*   Реализовать редактор диапазонов `RulesMatrixEditor.vue`.
*   Написать основной компонент генерации формы `DynamicSchemaForm.vue` с поддержкой Vuetify-валидации.
*   Написать единое окно диалога запуска `DynamicAnalysisDialog.vue` (которое заменит все старые диалоги).

### Шаг 4. Пилотное переключение и тесты
*   Переключить запуск плагинов `slope`, `aspect` и `hillshade` на новое динамическое окно.
*   Проверить корректность отправки DTO и валидации.
*   Удалить старые файлы `SlopeDialog.vue`, `AspectDialog.vue`, `HillshadeDialog.vue`.

### Шаг 5. Масштабирование на все 15 плагинов
*   Написать JSON-схемы для оставшихся 12 плагинов.
*   Удалить их устаревшие ручные диалоги во фронтенде.
*   Полностью очистить директорию `frontend/src/components/map/shared/` от дублирующегося кода диалогов.