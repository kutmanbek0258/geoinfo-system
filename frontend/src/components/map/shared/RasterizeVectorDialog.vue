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
  vectorSource: null as any,
  templateRasterSource: null as any,
  parameters: {
    attribute_field: '',
    default_value: 1.0,
    nodata_value: 0.0
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
      'vector_features': formData.value.vectorSource,
      'template_raster': formData.value.templateRasterSource
    };

    const parameters: Record<string, any> = {
      default_value: formData.value.parameters.default_value,
      nodata_value: formData.value.parameters.nodata_value
    };

    if (formData.value.parameters.attribute_field && formData.value.parameters.attribute_field.trim() !== '') {
      parameters.attribute_field = formData.value.parameters.attribute_field.trim();
    }

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'rasterize_vector',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run rasterize vector analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="500px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-grid-large"></v-icon>
        Растеризация вектора
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form" v-model="valid">
          <v-select
            v-model="formData.vectorSource"
            :items="vectorOptions"
            label="Исходный векторный слой"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Векторные слои не найдены"
          ></v-select>

          <v-select
            v-model="formData.templateRasterSource"
            :items="rasterOptions"
            label="Шаблонный растр (для охвата и разрешения)"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Растровые слои не найдены"
          ></v-select>

          <v-text-field
            v-model="formData.parameters.attribute_field"
            label="Поле атрибута вектора для записи в растр (опционально)"
            variant="outlined"
            density="comfortable"
            persistent-hint
            hint="Если не указано, будет использовано фиксированное значение"
          ></v-text-field>

          <v-row>
            <v-col cols="6">
              <v-text-field
                v-model.number="formData.parameters.default_value"
                label="Значение по умолчанию"
                type="number"
                variant="outlined"
                density="comfortable"
                required
                :rules="[v => v !== null && v !== undefined || 'Обязательно']"
              ></v-text-field>
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model.number="formData.parameters.nodata_value"
                label="Значение NoData"
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
          Запустить растеризацию
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
