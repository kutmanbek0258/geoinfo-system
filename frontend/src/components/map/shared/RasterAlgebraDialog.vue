<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { CreateAnalysisTaskDto, AnalysisDataSource, TerrainLayer, ImageryLayer } from '@/types/api';

const props = defineProps<{
  show: boolean;
}>();

const emit = defineEmits(['update:show', 'task-created']);

const store = useStore();
const valid = ref(false);
const loading = ref(false);

const internalShow = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
});

// Fetch data if missing when dialog is opened
watch(() => props.show, (newVal) => {
  if (newVal) {
    const projectId = store.state.geodata.selectedProjectId;
    if (projectId) {
      if (!store.state.geodata.imageryLayers) {
        store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 });
      }
      if (!store.state.geodata.terrainLayers) {
        store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
      }
      store.dispatch('geodata/fetchAnalysisTasksByProject', projectId);
    }
  }
});

interface VariableRow {
  name: string;
  source: any;
}

const formData = ref({
  expression: '',
  variables: [
    { name: 'A', source: null },
    { name: 'B', source: null }
  ] as VariableRow[]
});

function addVariable() {
  const nextChar = String.fromCharCode(65 + formData.value.variables.length); // A, B, C, D...
  const validChar = /^[A-Z]$/.test(nextChar) ? nextChar : 'X';
  formData.value.variables.push({
    name: validChar,
    source: null
  });
}

function removeVariable(index: number) {
  formData.value.variables.splice(index, 1);
}

const PLUGIN_LABELS: Record<string, string> = {
  terrain_contours:   'Изолинии рельефа',
  zonal_statistics:   'Зональная статистика',
  clip_raster_by_mask:'Обрезка растра',
  slope:              'Крутизна уклонов',
  aspect:             'Направление экспозиции',
  hillshade:          'Теневая отмывка',
  viewshed_analysis:  'Зоны видимости',
  spectral_indices:   'Спектральные индексы',
  unsupervised_class: 'Классификация K-Means',
  watershed_delineation: 'Выделение водосборов',
  polygonize_raster:  'Векторизация растра',
  rasterize_vector:   'Растеризация вектора',
  raster_algebra:     'Алгебра растров',
  raster_mosaic:      'Мозаика растров',
  raster_reclass:     'Реклассификация растра'
};

const pluginLabel = (name: string) => PLUGIN_LABELS[name] || name;

const rasterOptions = computed(() => {
  const imagery = store.state.geodata.imageryLayers?.content || [];
  const terrain = store.state.geodata.terrainLayers?.content || [];
  
  const items: any[] = imagery.map((l: ImageryLayer) => ({
    title: `[Снимок] ${l.name}`,
    value: { type: 'IMAGERY_LAYER', id: l.id }
  }));

  terrain.filter((l: TerrainLayer) => l.cogObjectKey).forEach((l: TerrainLayer) => {
    items.push({
      title: `[Рельеф] ${l.title}`,
      value: { type: 'TERRAIN_LAYER', id: l.id }
    });
  });

  const tasks = store.state.geodata.analysisTasks || [];
  tasks.filter((t: any) => t.status === 'COMPLETED' && t.s3OutputPaths?.raster_result)
    .forEach((t: any) => {
      items.push({
        title: `[Результат] ${pluginLabel(t.pluginName)} (${t.id.slice(0, 8)})`,
        value: {
          type: 'PREVIOUS_TASK_RESULT',
          taskId: t.id,
          outputKey: 'raster_result'
        }
      });
    });

  return items;
});

const isStoreLoading = computed(() => store.state.geodata.isLoading);

// Валидация всей формы
const isFormValid = computed(() => {
  if (!formData.value.expression.trim()) return false;
  if (formData.value.variables.length === 0) return false;
  
  const names = new Set<string>();
  for (const v of formData.value.variables) {
    if (!v.name || !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(v.name)) return false;
    if (!v.source) return false;
    if (names.has(v.name)) return false;
    names.add(v.name);
  }
  return true;
});

async function runAnalysis() {
  loading.value = true;
  try {
    const inputs: Record<string, AnalysisDataSource> = {};
    for (const v of formData.value.variables) {
      inputs[v.name] = v.source;
    }

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'raster_algebra',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: {
        expression: formData.value.expression
      }
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run raster algebra analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="600px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-calculator-variant"></v-icon>
        Картографическая алгебра растров
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form">
          <div class="text-subtitle-2 mb-2">Переменные формулы:</div>
          
          <v-row v-for="(v, index) in formData.variables" :key="index" class="align-center mb-2" dense>
            <v-col cols="3">
              <v-text-field
                v-model="v.name"
                label="Переменная"
                placeholder="A"
                variant="outlined"
                density="compact"
                hide-details
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
                :loading="isStoreLoading"
                no-data-text="Растровые слои не найдены"
              ></v-select>
            </v-col>
            <v-col cols="1" class="text-right">
              <v-btn icon="mdi-delete" variant="text" color="error" size="small" @click="removeVariable(index)"></v-btn>
            </v-col>
          </v-row>

          <v-btn prepend-icon="mdi-plus" variant="outlined" color="primary" class="mb-4 size-small" @click="addVariable">
            Добавить переменную
          </v-btn>

          <v-textarea
            v-model="formData.expression"
            label="Математическое выражение"
            placeholder="Например: (A - B) / (A + B + 0.001)"
            variant="outlined"
            density="comfortable"
            rows="3"
            persistent-hint
            hint="Используйте имена переменных (A, B...) и функции NumPy: np.where(A > 0, A, B), np.log(A), np.sin(A), abs(A) и т.д."
            required
            :rules="[v => !!v || 'Обязательно']"
          ></v-textarea>
        </v-form>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions class="pa-4">
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="internalShow = false">Отмена</v-btn>
        <v-btn color="primary" @click="runAnalysis" :loading="loading" :disabled="!isFormValid">
          Запустить вычисление
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
