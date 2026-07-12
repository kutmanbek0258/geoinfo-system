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
      if (!store.state.geodata.projectRasters) {
        store.dispatch('geodata/fetchProjectRasters', { page: 0, size: 100 });
      }
      if (!store.state.geodata.terrainLayers) {
        store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
      }
      store.dispatch('geodata/fetchAnalysisTasksByProject', projectId);
    }
  }
});

interface ReclassRule {
  min: number | null;
  max: number | null;
  value: number | null;
}

const formData = ref({
  rasterSource: null as any,
  parameters: {
    default_value: 0,
    rules: [
      { min: 0, max: 100, value: 1 },
      { min: 100, max: 200, value: 2 }
    ] as ReclassRule[]
  }
});

function addRule() {
  const lastRule = formData.value.parameters.rules[formData.value.parameters.rules.length - 1];
  const nextMin = lastRule ? lastRule.max : 0;
  const nextMax = nextMin !== null ? nextMin + 100 : 100;
  const nextVal = lastRule && lastRule.value !== null ? lastRule.value + 1 : 1;
  
  formData.value.parameters.rules.push({
    min: nextMin,
    max: nextMax,
    value: nextVal
  });
}

function removeRule(index: number) {
  formData.value.parameters.rules.splice(index, 1);
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
  const imagery = store.state.geodata.projectRasters?.content || [];
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
  if (!formData.value.rasterSource) return false;
  if (formData.value.parameters.rules.length === 0) return false;
  
  for (const r of formData.value.parameters.rules) {
    if (r.min === null || r.max === null || r.value === null) return false;
    if (isNaN(r.min) || isNaN(r.max) || isNaN(r.value)) return false;
    if (r.min >= r.max) return false;
  }
  return true;
});

async function runAnalysis() {
  loading.value = true;
  try {
    const inputs: Record<string, AnalysisDataSource> = {
      'source_raster': formData.value.rasterSource
    };

    // Сериализация правил в [[min, max, val], ...]
    const rulesList = formData.value.parameters.rules.map(r => [
      Number(r.min),
      Number(r.max),
      Number(r.value)
    ]);

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'raster_reclass',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: {
        default_value: Number(formData.value.parameters.default_value),
        rules: rulesList
      }
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run raster reclass analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="600px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-palette-swatch-outline"></v-icon>
        Реклассификация растра
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form">
          <v-select
            v-model="formData.rasterSource"
            :items="rasterOptions"
            label="Исходный растровый слой"
            variant="outlined"
            density="comfortable"
            required
            :loading="isStoreLoading"
            no-data-text="Растровые слои не найдены"
            class="mb-2"
          ></v-select>

          <v-text-field
            v-model.number="formData.parameters.default_value"
            label="Значение по умолчанию (для значений вне диапазонов)"
            type="number"
            variant="outlined"
            density="comfortable"
            required
            class="mb-4"
          ></v-text-field>

          <div class="text-subtitle-2 mb-2">Правила реклассификации (диапазоны [Min, Max)):</div>
          
          <v-row v-for="(rule, index) in formData.parameters.rules" :key="index" class="align-center mb-2" dense>
            <v-col cols="3">
              <v-text-field
                v-model.number="rule.min"
                label="Min (вкл.)"
                type="number"
                variant="outlined"
                density="compact"
                hide-details
                required
              ></v-text-field>
            </v-col>
            <v-col cols="3">
              <v-text-field
                v-model.number="rule.max"
                label="Max (искл.)"
                type="number"
                variant="outlined"
                density="compact"
                hide-details
                required
              ></v-text-field>
            </v-col>
            <v-col cols="4">
              <v-text-field
                v-model.number="rule.value"
                label="Новое значение"
                type="number"
                variant="outlined"
                density="compact"
                hide-details
                required
              ></v-text-field>
            </v-col>
            <v-col cols="2" class="text-right">
              <v-btn icon="mdi-delete" variant="text" color="error" size="small" @click="removeRule(index)"></v-btn>
            </v-col>
          </v-row>

          <v-btn prepend-icon="mdi-plus" variant="outlined" color="primary" class="size-small mt-2" @click="addRule">
            Добавить диапазон
          </v-btn>
        </v-form>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions class="pa-4">
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="internalShow = false">Отмена</v-btn>
        <v-btn color="primary" @click="runAnalysis" :loading="loading" :disabled="!isFormValid">
          Запустить реклассификацию
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
