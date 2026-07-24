# Спецификация инструментов (Skills/Tools) для LLM-ассистента

Данный документ является дополнением к [llm_integration_strategy.md](file:///C:/Users/admin/Documents/dev/geoinfo-system/docs/planned/llm_integration_strategy.md) и содержит детальное описание сигнатур, параметров в формате JSON Schema и примеров ответов для всех инструментов (функций), доступных LLM-агенту через механизм Function Calling.

---

## 1. Общие принципы работы Function Calling

Оркестратор `ai-adapter` передает список этих инструментов в каждом запросе к LLM API. 
* Модель анализирует запрос пользователя, решает, какой инструмент вызвать, и возвращает JSON-структуру с именем функции и аргументами.
* `ai-adapter` выполняет функцию (или делегирует ее в `ai-worker` через Kafka), собирает результат и возвращает его модели в сообщении с ролью `tool`.
* Модель на основе полученного результата формулирует итоговый ответ пользователю или запрашивает следующий шаг.

---

## 2. Спецификация инструментов

### 1. `get_project_metadata` — Чтение структуры и метаданных проекта
Позволяет модели получить обзор проекта: список слоев, папки и краткое содержание (без геометрий), если пользователь не закрепил контекст явно.

#### JSON Schema параметров:
```json
{
  "name": "get_project_metadata",
  "description": "Запрашивает метаданные проекта: слои, папки, перечень объектов и растров (включая BBox и системы координат). Вызывать, когда пользователь просит проанализировать проект в целом или если нужные слои не закреплены.",
  "parameters": {
    "type": "OBJECT",
    "properties": {
      "projectId": {
        "type": "STRING",
        "description": "UUID проекта, структуру которого необходимо получить."
      }
    },
    "required": ["projectId"]
  }
}
```

#### Пример ответа от системы (JSON):
```json
{
  "projectId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "projectName": "Проект Реконструкции Трассы М-4",
  "layers": [
    {
      "id": "e0a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5b",
      "name": "Ось автодороги (векторный)",
      "type": "VECTOR",
      "featuresCount": 12,
      "geometryType": "MultiLineString"
    },
    {
      "id": "f0a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5c",
      "name": "DEM Рельеф (растровый)",
      "type": "RASTER",
      "crs": "EPSG:32643",
      "bbox": "MULTIPOLYGON (((73.0 42.0, 74.0 42.0, 74.0 43.0, 73.0 43.0, 73.0 42.0)))"
    }
  ],
  "folders": [
    {
      "id": "c0a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5d",
      "name": "Изыскания 2026",
      "layerId": "e0a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5b",
      "objects": [
        { "id": "00a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5e", "name": "Пикет ПК-12", "type": "Point" }
      ]
    }
  ]
}
```

---

### 2. `execute_gis_task` — Выполнение ГИС-анализа в песочнице
Главный рабочий инструмент модели. Позволяет запустить произвольный скрипт на Python или команду Bash с передачей входных геоданных и установкой дополнительных библиотек.

#### JSON Schema параметров:
```json
{
  "name": "execute_gis_task",
  "description": "Запускает изолированный ГИС-расчет на сервере. Позволяет передать сгенерированный Python/Bash код, входные файлы из MinIO, перечень ожидаемых выходных файлов и дополнительные библиотеки.",
  "parameters": {
    "type": "OBJECT",
    "properties": {
      "scriptType": {
        "type": "STRING",
        "enum": ["PYTHON", "BASH"],
        "description": "Язык сценария. Предпочтителен PYTHON для расчетов с использованием geopandas/rasterio."
      },
      "code": {
        "type": "STRING",
        "description": "Полный текст скрипта. Входные файлы считываются из путей, переданных в inputs, выходные файлы должны быть сохранены по путям, указанным в outputs."
      },
      "inputs": {
        "type": "OBJECT",
        "description": "Словарь соответствия алиасов файлов их S3-путям (например: {'dem': 's3://gis-data/raw/dem.tif'}). Модель должна брать S3-пути из метаданных проекта."
      },
      "outputFiles": {
        "type": "ARRAY",
        "items": { "type": "STRING" },
        "description": "Список имен файлов, которые скрипт запишет на диск (в текущую рабочую директорию) и которые воркер должен загрузить в MinIO (например: ['result.geojson', 'output_stats.json'])."
      },
      "pipPackages": {
        "type": "ARRAY",
        "items": { "type": "STRING" },
        "description": "Опциональный список дополнительных библиотек Python, которые требуется установить перед запуском (например, ['networkx', 'scipy'])."
      }
    },
    "required": ["scriptType", "code", "inputs", "outputFiles"]
  }
}
```

#### Правила генерации Python-кода для `execute_gis_task`:
1. **Входные файлы**: Воркер автоматически скачивает файлы из секции `inputs` во временную папку песочницы. Скрипт должен считывать их из текущей директории по именам ключей (например, если `"inputs": {"dem_tif": "s3://..."}`, то в скрипте имя файла будет `"dem_tif"`).
2. **Выходные файлы**: Скрипт должен записывать результаты строго в текущую директорию с именами, указанными в `outputFiles`.
3. **Библиотеки**: По умолчанию доступны `rasterio`, `geopandas`, `shapely`, `numpy`, `rasterstats`, `pysheds`. Если нужны другие — указать их в `pipPackages`.

#### Пример вызова инструмента моделью (JSON):
```json
{
  "name": "execute_gis_task",
  "arguments": {
    "scriptType": "PYTHON",
    "code": "import geopandas as gpd\nroads = gpd.read_file('roads_geojson')\nbuffer = roads.buffer(0.001)\nbuffer.to_file('buffer_output.geojson', driver='GeoJSON')",
    "inputs": {
      "roads_geojson": "s3://gis-data/projects/1a2b/roads.geojson"
    },
    "outputFiles": ["buffer_output.geojson"],
    "pipPackages": []
  }
}
```

#### Пример успешного ответа от системы (JSON):
```json
{
  "status": "COMPLETED",
  "outputs": {
    "buffer_output.geojson": "s3://gis-data/temp/c9a6479b/buffer_output.geojson"
  },
  "executionTimeMs": 1420
}
```

---

### 3. `query_database_attributes` — Запрос семантики объектов (SQL-подобный фильтр)
Позволяет модели искать информацию в семантических свойствах (характеристиках) объектов слоя без выполнения тяжелого ГИС-анализа.

#### JSON Schema параметров:
```json
{
  "name": "query_database_attributes",
  "description": "Выполняет атрибутивный поиск по семантическим свойствам (characteristics) векторных объектов слоя в базе данных. Помогает отвечать на вопросы о количестве, названиях и свойствах объектов.",
  "parameters": {
    "type": "OBJECT",
    "properties": {
      "layerId": {
        "type": "STRING",
        "description": "UUID слоя, в объектах которого выполняется поиск."
      },
      "filterQuery": {
        "type": "STRING",
        "description": "SQL-подобное условие фильтрации для поля characteristics (например, \"status = 'active' AND type = 'highway'\")."
      },
      "limit": {
        "type": "INTEGER",
        "description": "Максимальное количество возвращаемых записей (по умолчанию 100)."
      }
    },
    "required": ["layerId", "filterQuery"]
  }
}
```

#### Пример ответа от системы (JSON):
```json
{
  "layerId": "e0a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5b",
  "matchedFeaturesCount": 2,
  "features": [
    {
      "id": "77a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5f",
      "name": "Мост через р. Нарын",
      "characteristics": {
        "status": "active",
        "material": "concrete",
        "length_m": 120
      }
    },
    {
      "id": "88a1f2b3-c4d5-6e7f-8a9b-0c1d2e3f4a5g",
      "name": "Мост через ручей",
      "characteristics": {
        "status": "active",
        "material": "metal",
        "length_m": 15
      }
    }
  ]
}
```

---

### 4. `get_task_status` — Контроль выполнения фоновых задач
Позволяет модели отслеживать ход выполнения тяжелых или асинхронных ГИС-операций в системе.

#### JSON Schema параметров:
```json
{
  "name": "get_task_status",
  "description": "Проверяет текущий статус выполнения асинхронной ГИС-задачи по её UUID. Полезно, если предыдущий вызов вернул статус RUNNING или PENDING.",
  "parameters": {
    "type": "OBJECT",
    "properties": {
      "taskId": {
        "type": "STRING",
        "description": "UUID проверяемой задачи."
      }
    },
    "required": ["taskId"]
  }
}
```

#### Пример ответа от системы (JSON):
```json
{
  "taskId": "c9a6479b-2b48-4cdb-86d7-2101df4c6b65",
  "status": "PROCESSING",
  "progressPercentage": 45,
  "startedAt": "2026-07-24T10:12:00Z"
}
```
