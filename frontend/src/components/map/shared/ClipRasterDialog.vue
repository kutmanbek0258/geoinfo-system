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
    }
  }
});

const formData = ref({
  rasterSource: null as any,
  vectorMask: null as any,
  parameters: {
    nodata_value: 0,
    crop_to_cutline: true
  } as Record<string, any>
});

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
      value: { type: 'VECTOR_LAYER', id: null }
    });
  }

  return options;
});

const isStoreLoading = computed(() => store.state.geodata.isLoading);

async function runAnalysis() {
  loading.value = true;
  try {
    const inputs: Record<string, AnalysisDataSource> = {
      'source_raster': formData.value.rasterSource,
      'mask_vector': formData.value.vectorMask
    };

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'clip_raster_by_mask',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: formData.value.parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to run clip raster analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="500px">
    <v-card>
      <v-card-title class="pa-4 bg-primary text-white">
        <v-icon start icon="mdi-crop-free"></v-icon>
        Обрезка растра по маске
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form" v-model="valid">
          <v-select
            v-model="formData.rasterSource"
            :items="rasterOptions"
            label="Исходный растр (Снимок или DEM)"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Растровые слои не найдены"
          ></v-select>

          <v-select
            v-model="formData.vectorMask"
            :items="vectorOptions"
            label="Векторная маска (Полигоны)"
            variant="outlined"
            density="comfortable"
            required
            :rules="[v => !!v || 'Обязательно']"
            :loading="isStoreLoading"
            no-data-text="Векторные слои (папки) не найдены"
          ></v-select>

          <v-row>
            <v-col cols="12">
              <v-text-field
                v-model.number="formData.parameters.nodata_value"
                label="Значение NoData"
                type="number"
                variant="outlined"
                density="comfortable"
                persistent-hint
                hint="Значение для пикселей вне маски"
              ></v-text-field>
            </v-col>
          </v-row>

          <v-checkbox
            v-model="formData.parameters.crop_to_cutline"
            label="Обрезать до границ маски"
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
          Запустить обрезку
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
