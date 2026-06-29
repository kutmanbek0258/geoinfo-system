<template>
  <v-menu
    v-model="menuOpen"
    :close-on-content-click="false"
    location="bottom end"
    offset="5"
    transition="slide-y-transition"
  >
    <template v-slot:activator="{ props }">
      <v-btn
        v-bind="props"
        icon="mdi-layers"
        color="white"
        class="mb-2"
        elevation="2"
        title="Imagery Layers"
      >
        <v-icon color="primary">mdi-layers</v-icon>
      </v-btn>
    </template>
    
    <v-card width="320">
      <v-card-title class="d-flex align-center py-2 px-4 bg-primary text-white">
        <v-icon class="mr-2">mdi-layers</v-icon>
        <span class="text-subtitle-1 font-weight-bold">Imagery Layers</span>
      </v-card-title>
      
      <v-divider></v-divider>
      
      <v-card-text class="pa-0" style="max-height: 450px; overflow-y: auto;">
        <v-list class="py-1">
          <v-list-item v-if="layers.length === 0" class="text-right text-grey py-4">
            No imagery layers found
          </v-list-item>
          <v-list-item v-for="layer in layers" :key="layer.id" class="px-4 py-1 flex-column">
            <v-checkbox
              :label="layer.name"
              :value="layer.id"
              :model-value="visibleIds"
              @update:model-value="val => emit('update:visibleIds', val)"
              @change="emit('toggle', layer)"
              hide-details
              density="compact"
              color="primary"
              class="w-100"
            ></v-checkbox>
            
            <div v-if="visibleIds.includes(layer.id)" class="w-100 pl-6 mt-n1">
              <v-slider
                :model-value="opacities[layer.id] || 100"
                @update:model-value="val => emit('update:opacity', { id: layer.id, value: val })"
                min="0"
                max="100"
                step="1"
                hide-details
                dense
                color="primary"
                class="px-2 mb-2"
              ></v-slider>

              <v-checkbox
                :model-value="getUseTiTilerColormap(layer)"
                label="Встроенная шкала TiTiler"
                density="compact"
                hide-details
                class="mt-0 mb-1"
                style="font-size: 11px;"
                @update:model-value="val => toggleTiTilerColormap(layer, !!val)"
              />

              <v-select
                v-if="getUseTiTilerColormap(layer)"
                :model-value="layer.colormapId || 'viridis'"
                :items="titilerColormaps"
                label="Шкала TiTiler"
                density="compact"
                variant="outlined"
                hide-details
                style="font-size: 11px;"
                class="mb-2"
                @update:model-value="val => emit('update:colormapId', { layerId: layer.id, colormapId: val })"
              />

              <v-select
                v-else
                :model-value="layer.style?.id || null"
                :items="styleSelectItems"
                item-title="title"
                item-value="id"
                label="Стиль интерполяции"
                density="compact"
                variant="outlined"
                hide-details
                clearable
                placeholder="Без стиля (Полутоновый)"
                style="font-size: 11px;"
                class="mb-2"
                @update:model-value="val => emit('update:style', { layerId: layer.id, styleId: val })"
              />

              <v-select
                :model-value="layer.resampling || 'bilinear'"
                :items="['nearest', 'bilinear', 'cubic', 'cubic_spline', 'lanczos', 'average', 'mode']"
                label="Метод resampling"
                density="compact"
                variant="outlined"
                hide-details
                style="font-size: 11px;"
                class="mb-2"
                @update:model-value="val => emit('update:resampling', { layerId: layer.id, resampling: val })"
              />
            </div>
            <v-divider class="my-1"></v-divider>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </v-menu>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import RasterStyleService from '@/services/raster-style.service';
import type { RasterStyle } from '@/types/api';

defineProps<{
  layers: any[];
  visibleIds: string[];
  opacities: Record<string, number>;
}>();

const emit = defineEmits([
  'update:visibleIds', 
  'toggle', 
  'update:opacity',
  'update:style',
  'update:colormapId',
  'update:resampling'
]);

const menuOpen = ref(false);
const useTiTilerMap = ref<Record<string, boolean>>({});

const availableStyles = ref<RasterStyle[]>([]);
const titilerColormaps = ref<string[]>([]);

const styleSelectItems = computed(() => {
  return availableStyles.value.map(s => ({
    title: s.title,
    id: s.id
  }));
});

const getUseTiTilerColormap = (layer: any) => {
  if (useTiTilerMap.value[layer.id] !== undefined) {
    return useTiTilerMap.value[layer.id];
  }
  const initialVal = !!(layer.colormapId || !layer.style?.id);
  useTiTilerMap.value[layer.id] = initialVal;
  return initialVal;
};

const toggleTiTilerColormap = (layer: any, val: boolean) => {
  useTiTilerMap.value[layer.id] = val;
  if (val) {
    const colormapId = layer.colormapId || 'viridis';
    emit('update:colormapId', { layerId: layer.id, colormapId });
    emit('update:style', { layerId: layer.id, styleId: null });
  } else {
    emit('update:colormapId', { layerId: layer.id, colormapId: null });
  }
};

const fetchAvailableStyles = async () => {
  try {
    const response = await RasterStyleService.getRasterStyles(0, 100);
    availableStyles.value = response.data.content;
  } catch (err) {
    console.error('Failed to fetch raster styles for layers control:', err);
  }
};

const fetchTiTilerColormaps = async () => {
  try {
    titilerColormaps.value = await RasterStyleService.getTiTilerColorMaps();
  } catch (err) {
    console.error('Failed to fetch TiTiler colormaps for layers control:', err);
    titilerColormaps.value = ['cividis', 'inferno', 'magma', 'plasma', 'rdylgn', 'spectral', 'terrain', 'viridis'];
  }
};

onMounted(() => {
  fetchAvailableStyles();
  fetchTiTilerColormaps();
});
</script>
