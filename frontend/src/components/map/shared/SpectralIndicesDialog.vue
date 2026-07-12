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

const formData = ref({
  indexType: 'NDVI',
  redSource: null as any,
  greenSource: null as any,
  nirSource: null as any,
  swirSource: null as any,
  reSource: null as any
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

const isStoreLoading = computed(() => store.state.geodata.isLoading);

async function runAnalysis() {
  loading.value = true;
  try {
    const inputs: Record<string, AnalysisDataSource> = {};
    
    if (formData.value.indexType === 'NDVI') {
      inputs['red'] = formData.value.redSource;
      inputs['nir'] = formData.value.nirSource;
    } else if (formData.value.indexType === 'NDWI') {
      inputs['green'] = formData.value.greenSource;
      inputs['nir'] = formData.value.nirSource;
    } else if (formData.value.indexType === 'NBR') {
      inputs['nir'] = formData.value.nirSource;
      inputs['swir'] = formData.value.swirSource;
    } else if (formData.value.indexType === 'NDRE') {
      inputs['nir'] = formData.value.nirSource;
      inputs['re'] = formData.value.reSource;
    }

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'spectral_indices',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: {
        index_type: formData.value.indexType
      }
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run spectral indices analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="500px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-math-compass"></v-icon>
        Расчет спектральных индексов
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form" v-model="valid">
          <v-select
            v-model="formData.indexType"
            :items="[
              { title: 'NDVI (Нормализованный вегетационный индекс)', value: 'NDVI' },
              { title: 'NDWI (Нормализованный водный индекс)', value: 'NDWI' },
              { title: 'NBR (Нормализованное отношение гари)', value: 'NBR' },
              { title: 'NDRE (Нормализованный разностный индекс по краю красного)', value: 'NDRE' }
            ]"
            label="Спектральный индекс"
            variant="outlined"
            density="comfortable"
            required
          ></v-select>

          <!-- NDVI inputs -->
          <template v-if="formData.indexType === 'NDVI'">
            <v-select
              v-model="formData.redSource"
              :items="rasterOptions"
              label="Красный канал (Red)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>

            <v-select
              v-model="formData.nirSource"
              :items="rasterOptions"
              label="Ближний ИК канал (NIR)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>
          </template>

          <!-- NDWI inputs -->
          <template v-else-if="formData.indexType === 'NDWI'">
            <v-select
              v-model="formData.greenSource"
              :items="rasterOptions"
              label="Зеленый канал (Green)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>

            <v-select
              v-model="formData.nirSource"
              :items="rasterOptions"
              label="Ближний ИК канал (NIR)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>
          </template>

          <!-- NBR inputs -->
          <template v-else-if="formData.indexType === 'NBR'">
            <v-select
              v-model="formData.nirSource"
              :items="rasterOptions"
              label="Ближний ИК канал (NIR)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>

            <v-select
              v-model="formData.swirSource"
              :items="rasterOptions"
              label="Коротковолновый ИК канал (SWIR)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>
          </template>

          <!-- NDRE inputs -->
          <template v-else-if="formData.indexType === 'NDRE'">
            <v-select
              v-model="formData.nirSource"
              :items="rasterOptions"
              label="Ближний ИК канал (NIR)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>

            <v-select
              v-model="formData.reSource"
              :items="rasterOptions"
              label="Край красного канал (RedEdge)"
              variant="outlined"
              density="comfortable"
              required
              :rules="[v => !!v || 'Обязательно']"
              :loading="isStoreLoading"
              no-data-text="Растровые слои не найдены"
            ></v-select>
          </template>
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
