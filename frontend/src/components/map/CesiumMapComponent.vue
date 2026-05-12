<template>
  <div ref="cesiumContainer" class="cesium-container">
    <!-- Оверлей выбора террейна -->
    <v-card class="cesium-overlay top-right">
      <v-card-title>Terrain Layers</v-card-title>
      <v-radio-group v-model="selectedTerrainId" hide-details class="px-4 pb-4">
        <v-radio label="World Terrain" :value="null"></v-radio>
        <v-radio v-for="layer in terrainLayers" :key="layer.id" :label="layer.title" :value="layer.id"></v-radio>
      </v-radio-group>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, computed } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
import type { TerrainLayer } from '@/types/api';

const props = defineProps<{
  projectId: string;
}>();

const store = useStore();
const cesiumContainer = ref<HTMLElement | null>(null);
let viewer: Cesium.Viewer | null = null;

const terrainLayers = computed<TerrainLayer[]>(() => store.state.geodata.terrainLayers?.content || []);
const selectedTerrainId = ref<string | null>(null);

onMounted(async () => {
  if (!cesiumContainer.value) return;

  viewer = new Cesium.Viewer(cesiumContainer.value, {
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    animation: false,
    timeline: false,
    baseLayerPicker: true,
    navigationHelpButton: false,
    homeButton: true,
    geocoder: false,
    sceneModePicker: true,
  });

  store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
});

onUnmounted(() => {
  if (viewer) {
    viewer.destroy();
  }
});

watch(selectedTerrainId, async (newId) => {
  if (!viewer) return;

  if (!newId) {
    viewer.terrainProvider = new Cesium.EllipsoidTerrainProvider();
  } else {
    const layer = terrainLayers.value.find(l => l.id === newId);
    if (layer) {
      viewer.terrainProvider = await Cesium.CesiumTerrainProvider.fromUrl(layer.terrainUrl);
    }
  }
});
</script>

<style scoped>
.cesium-container {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
  position: relative;
}

.cesium-overlay {
  position: absolute;
  background-color: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  padding: 8px;
  z-index: 1000;
}

.top-right {
  top: 100px;
  right: 10px;
  width: 250px;
}
</style>
