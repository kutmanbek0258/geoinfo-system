<template>
  <div>
    <!-- Step 1: Layer Selection Dialog -->
    <v-dialog v-model="selectionDialog" max-width="500px" persistent>
      <v-card>
        <v-card-title class="bg-primary text-white">
          <v-icon class="mr-2">mdi-compare</v-icon>
          Swipe Tool: Select Layers
        </v-card-title>
        <v-card-text class="pt-4">
          <v-select
            v-model="leftLayerId"
            :items="availableLayers"
            item-title="name"
            item-value="id"
            label="Left Side Layer"
            variant="outlined"
            class="mb-2"
          ></v-select>
          <v-select
            v-model="rightLayerId"
            :items="availableLayers"
            item-title="name"
            item-value="id"
            label="Right Side Layer"
            variant="outlined"
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
            Compare
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Step 2: Fullscreen Swipe View -->
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
            Comparing: {{ leftLayerName }} vs {{ rightLayerName }}
          </v-toolbar-title>
          <v-spacer></v-spacer>
          <v-btn icon @click="closeAll">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-toolbar>

        <v-card-text class="pa-0 position-relative fill-height overflow-hidden">
          <div ref="swipeMapContainer" class="swipe-map"></div>
          
          <!-- Vertical Slider (Handle) -->
          <div
            class="swipe-handle"
            :style="{ left: swipePosition + 'px' }"
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
import 'ol/ol.css';
import { Map, View } from 'ol';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import XYZ from 'ol/source/XYZ';
import type { ImageryLayer, ProjectPoint, ProjectMultiline, ProjectPolygon } from '@/types/api';
import { buildTiTilerStyleParams, getExtentFromGeometry } from '@/util/titiler-style-builder';

import { getRenderPixel } from 'ol/render';
import { createEmpty, extend } from 'ol/extent';
import { transformExtent } from 'ol/proj';
import { GeoJSON } from 'ol/format';

const props = defineProps({
  modelValue: Boolean,
});

const emit = defineEmits(['update:modelValue']);

const store = useStore();
const selectionDialog = ref(false);
const swipeViewDialog = ref(false);
const swipeMapContainer = ref<HTMLElement | null>(null);

const leftLayerId = ref<string | null>(null);
const rightLayerId = ref<string | null>(null);
const swipePosition = ref(window.innerWidth / 2);

let map: Map | null = null;
let leftWmsLayer: TileLayer<any> | null = null;
let rightWmsLayer: TileLayer<any> | null = null;


const availableLayers = computed<ImageryLayer[]>(() => store.state.geodata.imageryLayers?.content || []);

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
  destroyMap();
  emit('update:modelValue', false);
};

const backToSelection = () => {
  swipeViewDialog.value = false;
  selectionDialog.value = true;
  destroyMap();
};

const startSwipeView = async () => {
  selectionDialog.value = false;
  swipeViewDialog.value = true;
  await nextTick();
  initMap();
};

const initMap = () => {
  if (!swipeMapContainer.value) return;

  const leftInfo = availableLayers.value.find(l => l.id === leftLayerId.value);
  const rightInfo = availableLayers.value.find(l => l.id === rightLayerId.value);

  if (!leftInfo || !rightInfo) return;

  const leftColormapParam = buildTiTilerStyleParams(leftInfo.style, leftInfo.colormapId, leftInfo.resampling, leftInfo.characteristics);
  const leftS3Url = `s3://geo-abstraction-input/${leftInfo.cogObjectKey}`;
  const leftTileUrl = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(leftS3Url)}${leftColormapParam}`;

  let leftOlExtent: [number, number, number, number] | undefined;
  if (leftInfo.bbox) {
    const wgs84Extent = getExtentFromGeometry(leftInfo.bbox);
    if (wgs84Extent) {
      leftOlExtent = transformExtent(wgs84Extent, 'EPSG:4326', 'EPSG:3857') as [number, number, number, number];
    }
  }

  leftWmsLayer = new TileLayer({
    source: new XYZ({
      url: leftTileUrl,
      transition: 0,
    }),
    extent: leftOlExtent
  });

  const rightColormapParam = buildTiTilerStyleParams(rightInfo.style, rightInfo.colormapId, rightInfo.resampling, rightInfo.characteristics);
  const rightS3Url = `s3://geo-abstraction-input/${rightInfo.cogObjectKey}`;
  const rightTileUrl = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(rightS3Url)}${rightColormapParam}`;

  let rightOlExtent: [number, number, number, number] | undefined;
  if (rightInfo.bbox) {
    const wgs84Extent = getExtentFromGeometry(rightInfo.bbox);
    if (wgs84Extent) {
      rightOlExtent = transformExtent(wgs84Extent, 'EPSG:4326', 'EPSG:3857') as [number, number, number, number];
    }
  }

  rightWmsLayer = new TileLayer({
    source: new XYZ({
      url: rightTileUrl,
      transition: 0,
    }),
    extent: rightOlExtent
  });


  // The Magic: Cliping the right layer
  rightWmsLayer.on('prerender', (event) => {
    const ctx = event.context as CanvasRenderingContext2D;
    const mapSize = map?.getSize();
    if (!mapSize) return;

    const width = mapSize[0];
    const height = mapSize[1];
    
    // Calculate the clip region based on swipePosition
    // We use getRenderPixel to handle high DPI screens
    const tl = getRenderPixel(event, [swipePosition.value, 0]);
    const tr = getRenderPixel(event, [width, 0]);
    const bl = getRenderPixel(event, [swipePosition.value, height]);
    const br = getRenderPixel(event, [width, height]);

    ctx.save();
    ctx.beginPath();
    ctx.moveTo(tl[0], tl[1]);
    ctx.lineTo(tr[0], tr[1]);
    ctx.lineTo(br[0], br[1]);
    ctx.lineTo(bl[0], bl[1]);
    ctx.closePath();
    ctx.clip();
  });

  rightWmsLayer.on('postrender', (event) => {
    const ctx = event.context as CanvasRenderingContext2D;
    ctx.restore();
  });

  map = new Map({
    target: swipeMapContainer.value,
    layers: [
      new TileLayer({ source: new OSM() }),
      leftWmsLayer,
      rightWmsLayer
    ],
    view: new View({
      center: [0, 0],
      zoom: 2
    })
  });

  // Zoom to right layer boundaries (using the new bbox field)
  let extent = createEmpty();
  let extentFound = false;

  const geoJsonFormat = new GeoJSON();

  if (rightInfo.bbox) {
    try {
      const read = geoJsonFormat.readFeatures(rightInfo.bbox, {
        dataProjection: 'EPSG:4326',
        featureProjection: 'EPSG:3857'
      });
      const features = Array.isArray(read) ? read : [read];
      features.forEach(f => {
        const geom = f.getGeometry();
        if (geom) extend(extent, geom.getExtent());
      });
      if (extent[0] !== Infinity && extent[0] !== -Infinity) {
        extentFound = true;
      }
    } catch (e) {
      console.error('Failed to parse right layer bbox', e);
    }
  }

  // Fallback to characteristics if bbox is not present or failed
  if (!extentFound && rightInfo.characteristics?.extent && Array.isArray(rightInfo.characteristics.extent)) {
    extent = rightInfo.characteristics.extent;
    extentFound = true;
    if (Math.abs(extent[0]) <= 180 && Math.abs(extent[1]) <= 90) {
        extent = transformExtent(extent, 'EPSG:4326', 'EPSG:3857');
    }
  }

  if (extentFound) {
    map.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 1500 });
  }

  // Auto zoom to layers if possible
  swipePosition.value = swipeMapContainer.value.clientWidth / 2;
};

const destroyMap = () => {
  if (map) {
    map.setTarget(undefined);
    map = null;
  }
  leftWmsLayer = null;
  rightWmsLayer = null;
};

// --- Dragging Logic ---
let isDragging = false;

const startDragging = (e: MouseEvent | TouchEvent) => {
  isDragging = true;
  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', stopDragging);
  document.addEventListener('touchmove', onDrag);
  document.addEventListener('touchend', stopDragging);
};

const onDrag = (e: MouseEvent | TouchEvent) => {
  if (!isDragging || !swipeMapContainer.value) return;
  
  let clientX;
  if (e instanceof MouseEvent) {
    clientX = e.clientX;
  } else {
    clientX = e.touches[0].clientX;
  }

  const rect = swipeMapContainer.value.getBoundingClientRect();
  let x = clientX - rect.left;
  
  // Constrain x
  x = Math.max(0, Math.min(x, rect.width));
  swipePosition.value = x;
  
  // Force map to re-render to update clip
  map?.render();
};

const stopDragging = () => {
  isDragging = false;
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', stopDragging);
  document.removeEventListener('touchmove', onDrag);
  document.removeEventListener('touchend', stopDragging);
};

onUnmounted(destroyMap);
</script>

<style scoped>
.swipe-card {
  display: flex;
  flex-direction: column;
}

.swipe-map {
  width: 100%;
  height: 100%;
  background-color: #f0f0f0;
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
  align-center: center;
  justify-content: center;
  box-shadow: 0 0 10px rgba(0,0,0,0.5);
}

.fill-height {
  height: calc(100vh - 48px); /* Adjust based on toolbar height */
}
</style>
