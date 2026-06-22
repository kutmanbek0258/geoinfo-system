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
    observer_x: null as number | null,
    observer_y: null as number | null,
    observer_height: 2.0,
    max_distance: 10000.0
  } as Record<string, any>
});

// Наблюдение за выбором точки на карте
watch(() => store.state.geodata.selectedPoint, (newPoint) => {
  if (newPoint) {
    formData.value.parameters.observer_x = Number(newPoint.x.toFixed(6));
    formData.value.parameters.observer_y = Number(newPoint.y.toFixed(6));
    // Сбрасываем выбранную точку во Vuex, чтобы можно было выбрать её заново
    store.commit('geodata/SET_SELECTED_POINT', null);
    // Возвращаем диалоговое окно на экран
    internalShow.value = true;
  }
});

const isSelectingOnMap = computed(() => store.state.geodata.pointSelectionActive);

function startMapSelection() {
  store.commit('geodata/SET_POINT_SELECTION_ACTIVE', true);
  internalShow.value = false;
}

const PLUGIN_LABELS: Record<string, string> = {
  terrain_contours:   'Изолинии рельефа',
  zonal_statistics:   'Зональная статистика',
  clip_raster_by_mask:'Обрезка растра',
  slope:              'Крутизна уклонов',
  aspect:             'Направление экспозиции',
  hillshade:          'Теневая отмывка',
  viewshed_analysis:  'Зоны видимости',
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
      pluginName: 'viewshed_analysis',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: formData.value.parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run viewshed analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div>
    <v-dialog v-model="internalShow" max-width="500px">
      <v-card>
        <v-card-title class="pa-4 bg-primary text-white">
          <v-icon start icon="mdi-eye-outline"></v-icon>
          Расчет зон видимости
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

            <div class="d-flex align-center mb-4">
              <v-btn
                prepend-icon="mdi-map-marker-plus"
                color="secondary"
                variant="outlined"
                class="w-100"
                @click="startMapSelection"
              >
                Указать точку наблюдателя на карте
              </v-btn>
            </div>

            <v-row>
              <v-col cols="6">
                <v-text-field
                  v-model.number="formData.parameters.observer_x"
                  label="Долгота (Observer X)"
                  type="number"
                  variant="outlined"
                  density="comfortable"
                  required
                  :rules="[v => v !== null && v !== undefined || 'Обязательно']"
                ></v-text-field>
              </v-col>
              <v-col cols="6">
                <v-text-field
                  v-model.number="formData.parameters.observer_y"
                  label="Широта (Observer Y)"
                  type="number"
                  variant="outlined"
                  density="comfortable"
                  required
                  :rules="[v => v !== null && v !== undefined || 'Обязательно']"
                ></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="6">
                <v-text-field
                  v-model.number="formData.parameters.observer_height"
                  label="Высота наблюдателя (м)"
                  type="number"
                  variant="outlined"
                  density="comfortable"
                  required
                  :rules="[v => v !== null && v !== undefined || 'Обязательно']"
                ></v-text-field>
              </v-col>
              <v-col cols="6">
                <v-text-field
                  v-model.number="formData.parameters.max_distance"
                  label="Радиус обзора (м)"
                  type="number"
                  variant="outlined"
                  density="comfortable"
                  required
                  :rules="[v => v !== null && v !== undefined || 'Обязательно']"
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

    <!-- Подсказка для выбора точки на карте -->
    <v-snackbar
      v-model="isSelectingOnMap"
      timeout="-1"
      color="info"
      location="top"
    >
      <div class="d-flex align-center justify-space-between w-100">
        <span>Кликните по карте для выбора точки наблюдателя</span>
        <v-btn
          color="white"
          variant="text"
          size="small"
          class="ml-4"
          @click="store.commit('geodata/SET_POINT_SELECTION_ACTIVE', false); internalShow = true;"
        >
          Отмена
        </v-btn>
      </div>
    </v-snackbar>
  </div>
</template>
