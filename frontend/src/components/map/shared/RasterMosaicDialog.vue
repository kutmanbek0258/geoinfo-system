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
  tiles: [null, null] as any[],
  parameters: {
    resampling: 'nearest',
    nodata: null as number | null
  } as Record<string, any>
});

function addTile() {
  formData.value.tiles.push(null);
}

function removeTile(index: number) {
  formData.value.tiles.splice(index, 1);
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

// Валидация формы: хотя бы 2 непустых тайла
const isFormValid = computed(() => {
  const activeTiles = formData.value.tiles.filter(t => !!t);
  return activeTiles.length >= 2;
});

async function runAnalysis() {
  loading.value = true;
  try {
    const inputs: Record<string, AnalysisDataSource> = {};
    let activeIndex = 0;
    
    formData.value.tiles.forEach((tile) => {
      if (tile) {
        inputs[`tile_${activeIndex}`] = tile;
        activeIndex++;
      }
    });

    const parameters: Record<string, any> = {
      resampling: formData.value.parameters.resampling
    };
    if (formData.value.parameters.nodata !== null && formData.value.parameters.nodata !== '') {
      parameters.nodata = Number(formData.value.parameters.nodata);
    }

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'raster_mosaic',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run raster mosaic analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="550px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-checkerboard"></v-icon>
        Сшивка растровой мозаики
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form">
          <div class="text-subtitle-2 mb-2">Объединяемые растровые слои (тайлы):</div>
          
          <v-row v-for="(tile, index) in formData.tiles" :key="index" class="align-center mb-2" dense>
            <v-col cols="11">
              <v-select
                v-model="formData.tiles[index]"
                :items="rasterOptions"
                :label="`Растровый слой ${index + 1}`"
                variant="outlined"
                density="compact"
                hide-details
                :loading="isStoreLoading"
                no-data-text="Растровые слои не найдены"
              ></v-select>
            </v-col>
            <v-col cols="1" class="text-right">
              <v-btn icon="mdi-delete" variant="text" color="error" size="small" :disabled="formData.tiles.length <= 2" @click="removeTile(index)"></v-btn>
            </v-col>
          </v-row>

          <v-btn prepend-icon="mdi-plus" variant="outlined" color="primary" class="mb-4 size-small" @click="addTile">
            Добавить слой
          </v-btn>

          <v-row>
            <v-col cols="6">
              <v-select
                v-model="formData.parameters.resampling"
                :items="[
                  { title: 'Ближайший сосед', value: 'nearest' },
                  { title: 'Билинейная', value: 'bilinear' },
                  { title: 'Кубическая', value: 'cubic' },
                  { title: 'Кубический сплайн', value: 'cubicspline' },
                  { title: 'Фильтр Ланцоша', value: 'lanczos' },
                  { title: 'Среднее значение', value: 'average' },
                  { title: 'Мода (наиб. частое)', value: 'mode' }
                ]"
                label="Интерполяция"
                variant="outlined"
                density="comfortable"
                required
              ></v-select>
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model.number="formData.parameters.nodata"
                label="NoData значение (опционально)"
                type="number"
                variant="outlined"
                density="comfortable"
                persistent-hint
                hint="Игнорировать значение цвета при наложении"
              ></v-text-field>
            </v-col>
          </v-row>
        </v-form>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions class="pa-4">
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="internalShow = false">Отмена</v-btn>
        <v-btn color="primary" @click="runAnalysis" :loading="loading" :disabled="!isFormValid">
          Запустить сшивку
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
