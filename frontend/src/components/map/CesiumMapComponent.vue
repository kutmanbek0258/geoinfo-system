<template>
  <div ref="cesiumContainer" class="cesium-container"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import * as Cesium from 'cesium';

const props = defineProps<{
  terrainUrl?: string;
  projectId: string;
}>();

const cesiumContainer = ref<HTMLElement | null>(null);
let viewer: Cesium.Viewer | null = null;

onMounted(async () => {
  if (!cesiumContainer.value) return;

  viewer = new Cesium.Viewer(cesiumContainer.value, {
    terrainProvider: await Cesium.CesiumTerrainProvider.fromUrl(
      props.terrainUrl || 'https://assets.agi.com/stk-terrain/v1/tilesets/world/tiles'
    ),
    animation: false,
    timeline: false,
    baseLayerPicker: true,
    navigationHelpButton: false,
    homeButton: true,
    geocoder: false,
    sceneModePicker: true,
  });

  // Добавляем поддержку подложек, если нужно
});

onUnmounted(() => {
  if (viewer) {
    viewer.destroy();
  }
});

watch(() => props.terrainUrl, async (newUrl) => {
  if (viewer && newUrl) {
    viewer.terrainProvider = await Cesium.CesiumTerrainProvider.fromUrl(newUrl);
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
}
</style>
