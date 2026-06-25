<template>
  <div>
    <!-- Step 1: Layer & Terrain Selection Dialog -->
    <v-dialog v-model="selectionDialog" max-width="500px" persistent>
      <v-card>
        <v-card-title class="bg-primary text-white">
          <v-icon class="mr-2">mdi-compare</v-icon>
          3D Swipe Tool: Select Layers
        </v-card-title>
        <v-card-text class="pt-4">
          <v-select
            v-model="leftLayerId"
            :items="availableLayers"
            item-title="name"
            item-value="id"
            label="Left Side WMS Layer"
            variant="outlined"
            class="mb-2"
          ></v-select>
          <v-select
            v-model="rightLayerId"
            :items="availableLayers"
            item-title="name"
            item-value="id"
            label="Right Side WMS Layer"
            variant="outlined"
            class="mb-2"
          ></v-select>
          <v-select
            v-model="terrainLayerId"
            :items="terrainLayers"
            item-title="title"
            item-value="id"
            label="Terrain Layer (Optional)"
            variant="outlined"
            clearable
          ></v-select>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="closeAll">Cancel</v-btn>
          <v-btn
            color="primary"
            :disabled="!leftLayerId || !rightLayerId"
            @click="startSwipeView"
          >
            Compare in 3D
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Step 2: Fullscreen 3D Swipe View -->
    <v-dialog
      v-model="swipeViewDialog"
      fullscreen
      hide-overlay
      transition="dialog-bottom-transition"
    >
      <v-card class="swipe-card">
        <v-toolbar color="primary" density="compact">
          <v-btn icon @click="backToSelection">
            <v-icon>mdi-arrow-left</v-icon>
          </v-btn>
          <v-toolbar-title>
            3D Comparison: {{ leftLayerName }} vs {{ rightLayerName }}
          </v-toolbar-title>
          <v-spacer></v-spacer>
          <v-btn icon @click="closeAll">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-toolbar>

        <v-card-text class="pa-0 position-relative fill-height overflow-hidden">
          <div ref="cesiumContainer" class="swipe-cesium-container"></div>
          
          <!-- Vertical Slider (Handle) -->
          <div
            class="swipe-handle"
            :style="{ left: swipeScreenX + 'px' }"
            @mousedown="startDragging"
            @touchstart="startDragging"
          >
            <div class="swipe-line"></div>
            <div class="swipe-circle">
              <v-icon color="white">mdi-unfold-more-vertical</v-icon>
            </div>
          </div>
        </v-card-text>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, nextTick, watch } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
import type { ImageryLayer, TerrainLayer } from '@/types/api';
import { buildTiTilerStyleParams, getExtentFromGeometry } from '@/util/titiler-style-builder';

const props = defineProps({
  modelValue: Boolean,
});

const emit = defineEmits(['update:modelValue']);

const store = useStore();
const selectionDialog = ref(false);
const swipeViewDialog = ref(false);
const cesiumContainer = ref<HTMLElement | null>(null);

const leftLayerId = ref<string | null>(null);
const rightLayerId = ref<string | null>(null);
const terrainLayerId = ref<string | null>(null);

// swipeScreenX is pixel position for UI, swipePosition is 0-1 for Cesium
const swipeScreenX = ref(window.innerWidth / 2);

let viewer: Cesium.Viewer | null = null;
let leftLayer: Cesium.ImageryLayer | null = null;
let rightLayer: Cesium.ImageryLayer | null = null;

const availableLayers = computed<ImageryLayer[]>(() => store.state.geodata.imageryLayers?.content || []);
const terrainLayers = computed<TerrainLayer[]>(() => store.state.geodata.terrainLayers?.content || []);

const leftLayerName = computed(() => availableLayers.value.find((l: ImageryLayer) => l.id === leftLayerId.value)?.name || '');
const rightLayerName = computed(() => availableLayers.value.find((l: ImageryLayer) => l.id === rightLayerId.value)?.name || '');

// --- Handlers ---

watch(() => props.modelValue, (val) => {
  if (val) {
    selectionDialog.value = true;
  }
});

const closeAll = () => {
  selectionDialog.value = false;
  swipeViewDialog.value = false;
  destroyViewer();
  emit('update:modelValue', false);
};

const backToSelection = () => {
  swipeViewDialog.value = false;
  selectionDialog.value = true;
  destroyViewer();
};

const startSwipeView = async () => {
  selectionDialog.value = false;
  swipeViewDialog.value = true;
  await nextTick();
  initCesium();
};

const initCesium = async () => {
  if (!cesiumContainer.value) return;

  const leftInfo = availableLayers.value.find(l => l.id === leftLayerId.value);
  const rightInfo = availableLayers.value.find(l => l.id === rightLayerId.value);
  const terrainInfo = terrainLayers.value.find(l => l.id === terrainLayerId.value);

  if (!leftInfo || !rightInfo) return;

  viewer = new Cesium.Viewer(cesiumContainer.value, {
    baseLayer: new Cesium.ImageryLayer(new Cesium.OpenStreetMapImageryProvider({
      url: 'https://a.tile.openstreetmap.org/'
    })),
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    animation: false,
    timeline: false,
    baseLayerPicker: false,
    geocoder: false,
    homeButton: true,
    infoBox: false,
    navigationHelpButton: false,
    sceneModePicker: true,
    selectionIndicator: false,
  });

  // Setup Terrain
  if (terrainInfo) {
    viewer.terrainProvider = await Cesium.CesiumTerrainProvider.fromUrl(terrainInfo.terrainUrl);
  }

  // Add Left Layer
  const leftColormapParam = buildTiTilerStyleParams(leftInfo.style, leftInfo.colormapId, leftInfo.resampling);
  const leftS3Url = `s3://geo-abstraction-input/${leftInfo.cogObjectKey}`;
  const leftTileUrl = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(leftS3Url)}${leftColormapParam}`;

  let leftRectangle: Cesium.Rectangle | undefined;
  if (leftInfo.bbox) {
    const extent = getExtentFromGeometry(leftInfo.bbox);
    if (extent) {
      leftRectangle = Cesium.Rectangle.fromDegrees(extent[0], extent[1], extent[2], extent[3]);
    }
  }

  leftLayer = viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
    url: leftTileUrl,
    rectangle: leftRectangle
  }));
  leftLayer.splitDirection = Cesium.SplitDirection.LEFT;

  // Add Right Layer
  const rightColormapParam = buildTiTilerStyleParams(rightInfo.style, rightInfo.colormapId, rightInfo.resampling);
  const rightS3Url = `s3://geo-abstraction-input/${rightInfo.cogObjectKey}`;
  const rightTileUrl = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(rightS3Url)}${rightColormapParam}`;

  let rightRectangle: Cesium.Rectangle | undefined;
  if (rightInfo.bbox) {
    const extent = getExtentFromGeometry(rightInfo.bbox);
    if (extent) {
      rightRectangle = Cesium.Rectangle.fromDegrees(extent[0], extent[1], extent[2], extent[3]);
    }
  }

  rightLayer = viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
    url: rightTileUrl,
    rectangle: rightRectangle
  }));
  rightLayer.splitDirection = Cesium.SplitDirection.RIGHT;

  // Initial Split Position (Center)
  viewer.scene.splitPosition = 0.5;
  swipeScreenX.value = cesiumContainer.value.clientWidth / 2;

  // Zoom to right layer's extent if available
  if (rightInfo.bbox) {
    let allCoords: number[][] = [];
    if (rightInfo.bbox.type === 'Polygon') {
      allCoords = rightInfo.bbox.coordinates[0];
    } else if (rightInfo.bbox.type === 'MultiPolygon') {
      allCoords = rightInfo.bbox.coordinates[0][0];
    }

    if (allCoords.length > 0) {
      const cartographics = allCoords.map(c => Cesium.Cartographic.fromDegrees(c[0], c[1]));
      let rectangle = Cesium.Rectangle.fromCartographicArray(cartographics);
      
      // Add a small margin (20%)
      const width = rectangle.width;
      const height = rectangle.height;
      rectangle = new Cesium.Rectangle(
        rectangle.west - width * 0.1,
        rectangle.south - height * 0.1,
        rectangle.east + width * 0.1,
        rectangle.north + height * 0.1
      );

      viewer.camera.flyTo({
        destination: rectangle,
        duration: 0
      });
    }
  }
};

const destroyViewer = () => {
  if (viewer) {
    viewer.destroy();
    viewer = null;
  }
  leftLayer = null;
  rightLayer = null;
};

// --- Dragging Logic ---
let isDragging = false;

const startDragging = () => {
  isDragging = true;
  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', stopDragging);
  document.addEventListener('touchmove', onDrag);
  document.addEventListener('touchend', stopDragging);
};

const onDrag = (e: MouseEvent | TouchEvent) => {
  if (!isDragging || !cesiumContainer.value || !viewer) return;
  
  let clientX;
  if (e instanceof MouseEvent) {
    clientX = e.clientX;
  } else {
    clientX = e.touches[0].clientX;
  }

  const rect = cesiumContainer.value.getBoundingClientRect();
  let x = clientX - rect.left;
  
  // Constrain x
  x = Math.max(0, Math.min(x, rect.width));
  swipeScreenX.value = x;
  
  // Update Cesium split position (0.0 to 1.0)
  viewer.scene.splitPosition = x / rect.width;
};

const stopDragging = () => {
  isDragging = false;
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', stopDragging);
  document.removeEventListener('touchmove', onDrag);
  document.removeEventListener('touchend', stopDragging);
};

onUnmounted(destroyViewer);
</script>

<style scoped>
.swipe-card {
  display: flex;
  flex-direction: column;
}

.swipe-cesium-container {
  width: 100%;
  height: 100%;
  background-color: #000;
}

.swipe-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background-color: transparent;
  cursor: ew-resize;
  z-index: 10;
  transform: translateX(-50%);
}

.swipe-line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 4px;
  background-color: white;
  transform: translateX(-50%);
  box-shadow: 0 0 5px rgba(0,0,0,0.5);
}

.swipe-circle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 40px;
  height: 40px;
  background-color: #2196F3;
  border: 4px solid white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 10px rgba(0,0,0,0.5);
}

.fill-height {
  height: calc(100vh - 48px);
}
</style>
