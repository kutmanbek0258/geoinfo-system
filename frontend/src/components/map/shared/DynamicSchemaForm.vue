<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useStore } from 'vuex';
import MapPointPicker from './MapPointPicker.vue';
import RulesMatrixEditor from './RulesMatrixEditor.vue';
import ProjectDocumentPicker from './ProjectDocumentPicker.vue';
import VectorFieldSelector from './VectorFieldSelector.vue';

const props = defineProps<{
  schema: any;
  modelValue: {
    inputs: Record<string, any>;
    parameters: Record<string, any>;
  };
}>();

const emit = defineEmits(['update:modelValue', 'start-selection', 'end-selection']);

const store = useStore();

// --- Списки слоев для селекторов ---
const rasterOptions = computed(() => {
  const imagery = store.state.geodata.imageryLayers?.content || [];
  const terrain = store.state.geodata.terrainLayers?.content || [];
  
  const items: any[] = imagery.map((l: any) => ({
    title: `[Снимок] ${l.name}`,
    value: { type: 'IMAGERY_LAYER', id: l.id }
  }));

  terrain.filter((l: any) => l.cogObjectKey).forEach((l: any) => {
    items.push({
      title: `[Рельеф] ${l.title}`,
      value: { type: 'TERRAIN_LAYER', id: l.id }
    });
  });

  const tasks = store.state.geodata.analysisTasks || [];
  tasks.filter((t: any) => t.status === 'COMPLETED' && t.s3OutputPaths?.raster_result)
    .forEach((t: any) => {
      items.push({
        title: `[Результат] ${t.pluginName} (${t.id.slice(0, 8)})`,
        value: {
          type: 'PREVIOUS_TASK_RESULT',
          taskId: t.id,
          outputKey: 'raster_result'
        }
      });
    });

  return items;
});

const vectorOptions = computed(() => {
  const folders = store.state.geodata.folders || [];
  const options = folders.map((f: any) => ({
    title: f.name,
    value: { type: 'VECTOR_LAYER', id: f.id }
  }));

  const rootPolygons = store.state.geodata.polygons.filter((p: any) => !p.folderId);
  if (rootPolygons.length > 0) {
    options.push({
      title: 'Все полигоны в корне проекта',
      value: { type: 'VECTOR_LAYER', id: null }
    });
  }

  const tasks = store.state.geodata.analysisTasks || [];
  tasks.forEach((t: any) => {
    if (t.status === 'COMPLETED' && t.s3OutputPaths) {
      if (t.s3OutputPaths.vector_result) {
        options.push({
          title: `[Результат] ${t.pluginName} (${t.id.slice(0, 8)})`,
          value: {
            type: 'PREVIOUS_TASK_RESULT',
            taskId: t.id,
            outputKey: 'vector_result'
          }
        });
      } else if (t.s3OutputPaths.statistics_geojson) {
        options.push({
          title: `[Результат] ${t.pluginName} (${t.id.slice(0, 8)})`,
          value: {
            type: 'PREVIOUS_TASK_RESULT',
            taskId: t.id,
            outputKey: 'statistics_geojson'
          }
        });
      }
    }
  });

  return options;
});

const terrainOptions = computed(() => {
  const terrain = store.state.geodata.terrainLayers?.content || [];
  const items = terrain
    .filter((l: any) => l.status === 'READY' && l.cogObjectKey)
    .map((l: any) => ({
      title: `[Рельеф] ${l.title}`,
      value: { type: 'TERRAIN_LAYER', id: l.id }
    }));

  const tasks = store.state.geodata.analysisTasks || [];
  tasks.filter((t: any) => t.status === 'COMPLETED' && t.s3OutputPaths?.raster_result)
    .forEach((t: any) => {
      items.push({
        title: `[Результат] ${t.pluginName} (${t.id.slice(0, 8)})`,
        value: {
          type: 'PREVIOUS_TASK_RESULT',
          taskId: t.id,
          outputKey: 'raster_result'
        }
      });
    });

  return items;
});

const getLayerOptions = (format: string) => {
  if (format === 'raster-layer') return rasterOptions.value;
  if (format === 'vector-layer') return vectorOptions.value;
  if (format === 'terrain-layer') return terrainOptions.value;
  return [];
};

// --- Поддержка динамических переменных для Алгебры Растров ---
interface VariableItem {
  name: string;
  source: any;
}
const variablesList = ref<VariableItem[]>([]);

onMounted(() => {
  // Инициализация дефолтных значений параметров
  const paramsSchema = props.schema.properties?.parameters?.properties;
  if (paramsSchema) {
    Object.keys(paramsSchema).forEach(key => {
      if (props.modelValue.parameters[key] === undefined && paramsSchema[key].default !== undefined) {
        props.modelValue.parameters[key] = paramsSchema[key].default;
      }
    });
  }

  // Инициализация алгебры растров, если нужно
  const inputsSchema = props.schema.properties?.inputs;
  if (inputsSchema && inputsSchema['ui:widget'] === 'variables_array') {
    const existing = props.modelValue.inputs;
    if (existing && Object.keys(existing).length > 0) {
      variablesList.value = Object.keys(existing).map(name => ({
        name,
        source: existing[name]
      }));
    } else {
      variablesList.value = [
        { name: 'A', source: null },
        { name: 'B', source: null }
      ];
    }
  }
});

// Синхронизация переменных обратно в inputs
watch(variablesList, (newList) => {
  const inputsMap: Record<string, any> = {};
  newList.forEach(v => {
    if (v.name && v.source) {
      inputsMap[v.name] = v.source;
    }
  });
  props.modelValue.inputs = inputsMap;
}, { deep: true });

function addVariable() {
  const nextChar = String.fromCharCode(65 + variablesList.value.length); // A, B, C, D...
  variablesList.value.push({ name: nextChar, source: null });
}

function removeVariable(index: number) {
  variablesList.value.splice(index, 1);
}
</script>

<template>
  <v-form ref="form" class="dynamic-schema-form">
    <!-- 1. Пространственные входы (inputs) -->
    <div v-if="schema.properties?.inputs" class="mb-4">
      <div class="text-subtitle-2 mb-2 font-weight-bold text-grey-darken-3">Пространственные данные:</div>
      
      <!-- Случай А: Кастомный массив переменных (Алгебра растров) -->
      <div v-if="schema.properties.inputs['ui:widget'] === 'variables_array'">
        <v-row v-for="(v, index) in variablesList" :key="index" class="align-center mb-2" dense>
          <v-col cols="3">
            <v-text-field
              v-model="v.name"
              label="Переменная"
              placeholder="A"
              variant="outlined"
              density="compact"
              hide-details
              required
              :rules="[
                val => !!val || 'Имя обязательно',
                val => /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(val) || 'Неверный идентификатор'
              ]"
            ></v-text-field>
          </v-col>
          <v-col cols="8">
            <v-select
              v-model="v.source"
              :items="rasterOptions"
              label="Растровый слой"
              variant="outlined"
              density="compact"
              hide-details
              required
              :rules="[v => !!v || 'Обязательно']"
              no-data-text="Растровые слои не найдены"
            ></v-select>
          </v-col>
          <v-col cols="1" class="text-right">
            <v-btn
              icon="mdi-delete"
              variant="text"
              color="error"
              size="small"
              :disabled="variablesList.length <= 1"
              @click="removeVariable(index)"
            ></v-btn>
          </v-col>
        </v-row>
        <v-btn
          prepend-icon="mdi-plus"
          variant="outlined"
          color="primary"
          size="small"
          class="mt-1"
          @click="addVariable"
        >
          Добавить переменную
        </v-btn>
      </div>

      <!-- Случай Б: Стандартные фиксированные входы схемы -->
      <div v-else>
        <div v-for="(inputVal, inputKey) in schema.properties.inputs.properties" :key="inputKey" class="mb-3">
          
          <!-- Селектор документа проекта (DXF) -->
          <ProjectDocumentPicker
            v-if="inputVal.format === 'project-document'"
            v-model="modelValue.inputs[inputKey]"
            :title="inputVal.title"
            :allowed-extensions="inputVal.allowedExtensions || ['.dxf']"
          />

          <!-- Стандартные ГИС-селекторы слоев -->
          <v-select
            v-else
            v-model="modelValue.inputs[inputKey]"
            :items="getLayerOptions(inputVal.format)"
            :label="inputVal.title"
            variant="outlined"
            density="comfortable"
            hide-details
            required
            :rules="[v => !!v || 'Обязательно']"
          ></v-select>
        </div>
      </div>
    </div>

    <v-divider v-if="schema.properties?.inputs && schema.properties?.parameters" class="my-4"></v-divider>

    <!-- 2. Параметры алгоритма (parameters) -->
    <div v-if="schema.properties?.parameters">
      <div class="text-subtitle-2 mb-2 font-weight-bold text-grey-darken-3">Параметры:</div>
      <div v-for="(paramVal, paramKey) in schema.properties.parameters.properties" :key="paramKey" class="mb-3">
        
        <!-- Булево значение -> Переключатель -->
        <v-switch
          v-if="paramVal.type === 'boolean'"
          v-model="modelValue.parameters[paramKey]"
          :label="paramVal.title"
          color="primary"
          density="comfortable"
          hide-details
        ></v-switch>

        <!-- Перечисление -> Выпадающий список -->
        <v-select
          v-else-if="paramVal.enum"
          v-model="modelValue.parameters[paramKey]"
          :items="paramVal.enum"
          :label="paramVal.title"
          variant="outlined"
          density="comfortable"
          hide-details
        ></v-select>

        <!-- Точка на карте -->
        <MapPointPicker
          v-else-if="paramVal.format === 'map-point'"
          v-model="modelValue.parameters[paramKey]"
          :title="paramVal.title"
          @start-selection="emit('start-selection')"
          @end-selection="emit('end-selection')"
        />

        <!-- Выбор поля атрибута векторного слоя -->
        <VectorFieldSelector
          v-else-if="paramVal.format === 'vector-field-select'"
          v-model="modelValue.parameters[paramKey]"
          :title="paramVal.title"
          :vector-source="modelValue.inputs[paramVal.vectorSourceRef]"
        />

        <!-- Матрица реклассификации -->
        <div v-else-if="paramVal['ui:widget'] === 'reclass_rules'">
          <div class="text-subtitle-2 mb-2 text-grey-darken-2">{{ paramVal.title }}</div>
          <RulesMatrixEditor
            v-model="modelValue.parameters[paramKey]"
          />
        </div>

        <!-- Текстовое или числовое поле -->
        <v-text-field
          v-else
          v-model="modelValue.parameters[paramKey]"
          :label="paramVal.title"
          :type="paramVal.type === 'number' || paramVal.type === 'integer' ? 'number' : 'text'"
          variant="outlined"
          density="comfortable"
          hide-details
          :placeholder="paramVal.placeholder"
          :rules="[
            val => {
              if (schema.properties.parameters.required?.includes(paramKey) && (val === null || val === undefined || val === '')) {
                return 'Поле обязательно';
              }
              if (paramVal.pattern && !new RegExp(paramVal.pattern).test(val)) {
                return 'Неверный формат';
              }
              return true;
            }
          ]"
        ></v-text-field>
      </div>
    </div>
  </v-form>
</template>
