<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { CreateAnalysisTaskDto, AnalysisDataSource } from '@/types/api';

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
      if (!store.state.geodata.folders || store.state.geodata.folders.length === 0) {
        store.dispatch('geodata/fetchFolders', projectId);
      }
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

const formData = ref({
  rasterSource: null as any,
  vectorSource: null as any,
  parameters: {
    stats: ['mean', 'count', 'min', 'max'],
    categorical: false
  } as Record<string, any>
});

const PLUGIN_LABELS: Record<string, string> = {
  terrain_contours:   'Изолинии рельефа',
  zonal_statistics:   'Зональная статистика',
  clip_raster_by_mask:'Обрезка растра',
};

const pluginLabel = (name: string) => PLUGIN_LABELS[name] || name;

const rasterOptions = computed(() => {
  const imagery = store.state.geodata.imageryLayers?.content || [];
  const items = imagery.map((l: any) => ({
    title: `[Снимок] ${l.name}`,
    value: { type: 'IMAGERY_LAYER', id: l.id }
  }));

  const terrain = store.state.geodata.terrainLayers?.content || [];
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

const vectorOptions = computed(() => {
  const folders = store.state.geodata.folders || [];
  const options = folders.map((f: any) => ({
    title: f.name,
    value: { type: 'VECTOR_LAYER', id: f.id }
  }));

  // Also include polygons that are not in folders if any
  const rootPolygons = store.state.geodata.polygons.filter((p: any) => !p.folderId);
  if (rootPolygons.length > 0) {
    options.push({
      title: 'Все полигоны в корне проекта',
      value: { type: 'VECTOR_LAYER', id: null }  // null → backend uses projectId
    });
  }

  const tasks = store.state.geodata.analysisTasks || [];
  tasks.forEach((t: any) => {
    if (t.status === 'COMPLETED' && t.s3OutputPaths) {
      if (t.s3OutputPaths.vector_result) {
        options.push({
          title: `[Результат] ${pluginLabel(t.pluginName)} (${t.id.slice(0, 8)})`,
          value: {
            type: 'PREVIOUS_TASK_RESULT',
            taskId: t.id,
            outputKey: 'vector_result'
          }
        });
      } else if (t.s3OutputPaths.statistics_geojson) {
        options.push({
          title: `[Результат] ${pluginLabel(t.pluginName)} (${t.id.slice(0, 8)})`,
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

const isStoreLoading = computed(() => store.state.geodata.isLoading);

async function runAnalysis() {
  loading.value = true;
  try {
    const inputs: Record<string, AnalysisDataSource> = {
      'source_raster': formData.value.rasterSource,
      'zones_vector': formData.value.vectorSource
    };

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'zonal_statistics',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: formData.value.parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run zonal statistics analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="500px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-chart-bar"></v-icon>
        Зональная статистика
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form" v-model="valid">
          <v-select
            v-model="formData.rasterSource"
            :items="rasterOptions"
            label="Источник растра"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Растровые слои не найдены"
          ></v-select>

          <v-select
            v-model="formData.vectorSource"
            :items="vectorOptions"
            label="Источник зон (вектор)"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Векторные слои (папки) не найдены"
          ></v-select>

          <v-select
            v-model="formData.parameters.stats"
            :items="['mean', 'count', 'min', 'max', 'sum', 'std', 'median']"
            label="Статистика"
            multiple
            chips
            variant="outlined"
            density="comfortable"
          ></v-select>
        </v-form>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions class="pa-4">
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="internalShow = false">Отмена</v-btn>
        <v-btn color="primary" @click="runAnalysis" :loading="loading" :disabled="!valid">
          Запустить анализ
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
