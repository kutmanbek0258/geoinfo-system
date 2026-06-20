<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { CreateAnalysisTaskDto, AnalysisDataSource, TerrainLayer } from '@/types/api';

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
      if (!store.state.geodata.terrainLayers) {
        store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
      }
      store.dispatch('geodata/fetchAnalysisTasksByProject', projectId);
    }
  }
});

const formData = ref({
  terrainSource: null as any,
  parameters: {
    interval: 10,
    base: 0,
    use_3d: true
  } as Record<string, any>
});

const PLUGIN_LABELS: Record<string, string> = {
  terrain_contours:   'Изолинии рельефа',
  zonal_statistics:   'Зональная статистика',
  clip_raster_by_mask:'Обрезка растра',
};

const pluginLabel = (name: string) => PLUGIN_LABELS[name] || name;

const terrainOptions = computed(() => {
  const layers = store.state.geodata.terrainLayers?.content || [];
  const items = layers
    .filter((l: TerrainLayer) => l.status === 'READY' && l.cogObjectKey)
    .map((l: TerrainLayer) => ({
      title: `[Рельеф] ${l.title}`,
      value: { type: 'TERRAIN_LAYER', id: l.id }
    }));

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
      'dem_file': formData.value.terrainSource
    };

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'terrain_contours',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: formData.value.parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run terrain contours analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="500px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-terrain"></v-icon>
        Генерация изолиний
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form" v-model="valid">
          <v-select
            v-model="formData.terrainSource"
            :items="terrainOptions"
            label="Источник рельефа (DEM)"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Слои рельефа (READY) не найдены"
          ></v-select>

          <v-row>
            <v-col cols="6">
              <v-text-field
                v-model.number="formData.parameters.interval"
                label="Интервал (м)"
                type="number"
                variant="outlined"
                density="comfortable"
                required
              ></v-text-field>
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model.number="formData.parameters.base"
                label="База (м)"
                type="number"
                variant="outlined"
                density="comfortable"
              ></v-text-field>
            </v-col>
          </v-row>

          <v-checkbox
            v-model="formData.parameters.use_3d"
            label="Создавать 3D геометрию (Z-координату)"
            density="compact"
            hide-details
          ></v-checkbox>
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
