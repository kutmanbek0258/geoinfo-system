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
      if (!store.state.geodata.globalRasters || store.state.geodata.globalRasters.length === 0) {
        store.dispatch('geodata/fetchGlobalRasters');
      }
      store.dispatch('geodata/fetchAnalysisTasksByProject', projectId);
    }
  }
});

const formData = ref({
  rasterSource: null as any,
  parameters: {
    clusters_count: 5,
    max_iter: 100
  } as Record<string, any>
});

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
  const globalRasters = store.state.geodata.globalRasters || [];
  
  const items: any[] = imagery.map((l: ImageryLayer) => ({
    title: `[Снимок] ${l.name}`,
    value: { type: 'IMAGERY_LAYER', id: l.id }
  }));

  globalRasters.forEach((l: any) => {
    items.push({
      title: `[Глобальный] ${l.name}`,
      value: { type: 'IMAGERY_LAYER', id: l.id }
    });
  });

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

async function runAnalysis() {
  loading.value = true;
  try {
    const inputs: Record<string, AnalysisDataSource> = {
      'multiband_raster': formData.value.rasterSource
    };

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'unsupervised_class',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: formData.value.parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run unsupervised class analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="500px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-chart-scatter-plot"></v-icon>
        Неконтролируемая классификация K-Means
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form" v-model="valid">
          <v-select
            v-model="formData.rasterSource"
            :items="rasterOptions"
            label="Исходный растр (многоканальный или DEM)"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Растровые слои не найдены"
          ></v-select>

          <v-row>
            <v-col cols="6">
              <v-text-field
                v-model.number="formData.parameters.clusters_count"
                label="Количество классов"
                type="number"
                variant="outlined"
                density="comfortable"
                required
                :rules="[
                  v => v !== null && v !== undefined || 'Обязательно',
                  v => v >= 2 && v <= 30 || 'От 2 до 30'
                ]"
              ></v-text-field>
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model.number="formData.parameters.max_iter"
                label="Макс. итераций"
                type="number"
                variant="outlined"
                density="comfortable"
                required
                :rules="[
                  v => v !== null && v !== undefined || 'Обязательно',
                  v => v >= 10 && v <= 1000 || 'От 10 до 1000'
                ]"
              ></v-text-field>
            </v-col>
          </v-row>
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
