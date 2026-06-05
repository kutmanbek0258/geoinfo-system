<template>
  <div ref="cesiumParent" class="cesium-container">
    <!-- Карта Cesium -->
    <div ref="cesiumContainer" class="cesium-viewer"></div>

    <!-- Оверлей 1: Переключатели слоев с выпадающими списками -->
    <div class="map-overlay top-right-layers d-flex flex-column align-end">
      <!-- Кнопка Imagery Layers -->
      <v-menu
        v-model="imageryMenuOpen"
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
        
        <v-card width="280">
          <v-card-title class="d-flex align-center py-2 px-4 bg-primary text-white">
            <v-icon class="mr-2">mdi-layers</v-icon>
            <span class="text-subtitle-1 font-weight-bold">Imagery Layers</span>
          </v-card-title>
          
          <v-divider></v-divider>
          
          <!-- Прокручиваемый список фиксированной высоты -->
          <v-card-text class="pa-0" style="max-height: 250px; overflow-y: auto;">
            <v-list dense class="py-1">
              <v-list-item v-if="imageryLayers.length === 0" class="text-center text-grey py-4">
                No imagery layers found
              </v-list-item>
              <v-list-item v-for="layer in imageryLayers" :key="layer.id" class="px-4 py-1">
                <v-checkbox
                  :label="layer.name"
                  :value="layer.id"
                  v-model="visibleLayerIds"
                  @update:modelValue="toggleImageryLayer(layer)"
                  hide-details
                  density="compact"
                  color="primary"
                  class="w-100"
                ></v-checkbox>
                <v-slider
                  v-if="visibleLayerIds.includes(layer.id)"
                  :model-value="layerOpacities[layer.id] || 100"
                  @update:modelValue="newOpacity => setLayerOpacity(layer.id, newOpacity)"
                  min="0"
                  max="100"
                  step="1"
                  hide-details
                  dense
                  color="primary"
                  class="mt-n2 px-2"
                ></v-slider>
              </v-list-item>
            </v-list>
          </v-card-text>
        </v-card>
      </v-menu>

      <!-- Кнопка Terrain Layers -->
      <v-menu
        v-model="terrainMenuOpen"
        :close-on-content-click="false"
        location="bottom end"
        offset="5"
        transition="slide-y-transition"
      >
        <template v-slot:activator="{ props }">
          <v-btn
            v-bind="props"
            icon="mdi-terrain"
            color="white"
            class="mb-2"
            elevation="2"
            title="Terrain Layers"
          >
            <v-icon color="primary">mdi-terrain</v-icon>
          </v-btn>
        </template>
        
        <v-card width="280">
          <v-card-title class="d-flex align-center py-2 px-4 bg-primary text-white">
            <v-icon class="mr-2">mdi-terrain</v-icon>
            <span class="text-subtitle-1 font-weight-bold">Terrain Layers</span>
          </v-card-title>
          
          <v-divider></v-divider>
          
          <!-- Прокручиваемый список фиксированной высоты -->
          <v-card-text class="pa-0" style="max-height: 200px; overflow-y: auto;">
            <v-radio-group v-model="selectedTerrainId" hide-details class="px-4 py-2">
              <v-radio label="World Terrain" :value="null" color="primary" density="compact" class="mb-1"></v-radio>
              <v-radio
                v-for="layer in terrainLayers"
                :key="layer.id"
                :label="layer.title"
                :value="layer.id"
                color="primary"
                density="compact"
                class="mb-1"
              ></v-radio>
            </v-radio-group>
          </v-card-text>
        </v-card>
      </v-menu>

      <!-- Кнопка Zoom to Extent -->
      <v-btn
        icon="mdi-magnify-scan"
        color="white"
        class="mb-2"
        elevation="2"
        @click="zoomToExtent"
        title="Zoom to extent"
      >
        <v-icon color="primary">mdi-magnify-scan</v-icon>
      </v-btn>

      <!-- Кнопка Auto Extent Toggle -->
      <v-btn
        icon="mdi-target-variant"
        :color="autoExtentEnabled ? 'primary' : 'white'"
        class="mb-2"
        elevation="2"
        @click="autoExtentEnabled = !autoExtentEnabled"
        :title="autoExtentEnabled ? 'Auto Extent Enabled' : 'Auto Extent Disabled'"
      >
        <v-icon :color="autoExtentEnabled ? 'white' : 'primary'">mdi-target-variant</v-icon>
      </v-btn>
    </div>

    <!-- Оверлей 3: Кнопки добавления -->
    <div class="map-overlay bottom-right d-flex flex-column align-end">
      <!-- Кнопка деактивации (только когда что-то активно) -->
      <v-fade-transition>
        <v-btn
          v-if="measureMode || isBufferMode || drawMode"
          icon="mdi-close"
          color="error"
          class="mb-2"
          elevation="4"
          @click="stopActiveTool"
          title="Stop tool"
        >
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-fade-transition>

      <v-btn
        icon="mdi-compare"
        color="white"
        class="mb-2"
        elevation="2"
        @click="swipeActive = true"
        title="Swipe Tool (Compare Layers)"
      >
        <v-icon color="primary">mdi-compare</v-icon>
      </v-btn>

      <v-btn
        icon="mdi-file-import"
        color="primary"
        class="mb-2"
        @click="openImportFileDialog"
        title="Import KML/KMZ to this project"
      ></v-btn>
      
      <!-- Инструменты измерения (Dropdown) -->
      <v-menu location="left">
        <template v-slot:activator="{ props }">
          <v-btn
            v-bind="props"
            icon="mdi-ruler"
            :color="measureMode || isBufferMode ? 'primary' : 'white'"
            class="mb-2"
            elevation="2"
            title="Measurement Tools"
          >
            <v-icon :color="measureMode || isBufferMode ? 'white' : 'primary'">mdi-ruler</v-icon>
          </v-btn>
        </template>
        <v-list density="compact">
          <v-list-item 
            prepend-icon="mdi-ruler" 
            title="Distance" 
            @click="measureMode = (measureMode === 'length' ? null : 'length')"
            :active="measureMode === 'length'"
            color="primary"
          ></v-list-item>
          <v-list-item 
            prepend-icon="mdi-vector-square" 
            title="Area" 
            @click="measureMode = (measureMode === 'area' ? null : 'area')"
            :active="measureMode === 'area'"
            color="primary"
          ></v-list-item>
          <v-list-item 
            prepend-icon="mdi-radius-outline" 
            title="Buffer Zone" 
            @click="isBufferMode = !isBufferMode"
            :active="isBufferMode"
            color="primary"
          ></v-list-item>
          <v-divider></v-divider>
          <v-list-item 
            prepend-icon="mdi-trash-can-outline" 
            title="Clear Measurements" 
            @click="clearMeasurements"
            color="error"
          ></v-list-item>
        </v-list>
      </v-menu>

      <v-btn-toggle v-model="drawMode" variant="elevated" density="comfortable">
        <v-btn value="Point" title="Add Point">
          <v-icon>mdi-map-marker</v-icon>
        </v-btn>
        <v-btn value="MultiLineString" title="Add Line">
          <v-icon>mdi-vector-polyline</v-icon>
        </v-btn>
        <v-btn value="Polygon" title="Add Polygon">
          <v-icon>mdi-vector-polygon</v-icon>
        </v-btn>
      </v-btn-toggle>
    </div>

    <!-- Оверлей 6: Панель настройки буфера -->
    <v-fade-transition>
      <div v-if="isBufferMode" class="map-overlay buffer-panel">
        <div class="d-flex align-center justify-space-between mb-2">
          <span class="text-subtitle-2 font-weight-bold">Buffer Zone (3D)</span>
          <v-btn icon="mdi-close" size="x-small" variant="text" @click="isBufferMode = false"></v-btn>
        </div>
        
        <div v-if="!bufferSourceEntity" class="text-caption text-grey mb-2">
          Click on map to set center
        </div>
        
        <template v-else>
          <div class="text-caption mb-1">Center: [{{ bufferCenterCoords[0].toFixed(4) }}, {{ bufferCenterCoords[1].toFixed(4) }}]</div>
          <v-slider
            v-model="bufferDistance"
            min="1"
            max="10000"
            step="1"
            hide-details
            thumb-label
            color="primary"
            label="Radius (m)"
            class="mb-2"
          ></v-slider>
          <v-text-field
            v-model.number="bufferDistance"
            type="number"
            density="compact"
            hide-details
            suffix="meters"
            variant="outlined"
            class="mb-1"
          ></v-text-field>
        </template>
      </div>
    </v-fade-transition>

    <!-- Measurement Tooltips -->
    <div v-for="(tooltip, idx) in measureTooltips" :key="idx" 
         class="cesium-tooltip" 
         :style="getTooltipStyle(tooltip.position)">
      {{ tooltip.text }}
    </div>

    <!-- Оверлей Shot Frame (Визуальная рамка для редактирования) -->
    <v-fade-transition>
      <div v-if="isGeometryEditMode && isZoomHighEnough" class="shot-frame-overlay">
        <div class="shot-frame-content">
          <div class="shot-frame-label">SHOT FRAME ACTIVE (3D)</div>
        </div>
      </div>
    </v-fade-transition>

    <!-- Import File Dialog -->
    <v-dialog v-model="importFileDialog" max-width="500px">
      <v-card>
        <v-card-title>Import KML/KMZ to Project</v-card-title>
        <v-card-text>
          <v-file-input
            v-model="importFile"
            label="Select KML or KMZ File"
            accept=".kml,.kmz"
            prepend-icon="mdi-file-xml"
            show-size
          ></v-file-input>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="importFileDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="executeFileImport" :loading="isImporting">Import</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Оверлей 4: Детали объекта -->
    <div v-if="selectedFeatureId && !isGeometryEditMode"
        class="map-overlay top-right-details">
        <ObjectDetails
            :feature-id="selectedFeatureId"
            :feature-name="selectedFeature?.name"
            :feature-description="selectedFeature?.description"
            :feature-type="selectedFeature?.type || ''"
            :feature-image-url="selectedFeature?.imageUrl"
            :full-feature-data="selectedFeature"
            @close="store.dispatch('geodata/selectFeature', null)"
            @edit-geometry="enterGeometryEditMode"
        />
    </div>

    <!-- Оверлей 5: Подтверждение изменения геометрии -->
    <div v-if="isGeometryEditMode" class="map-overlay bottom-right-edit">
      <v-btn icon="mdi-check" color="success" class="mr-2" @click="confirmGeometryEdit" title="Confirm Changes"></v-btn>
      <v-btn icon="mdi-close" color="error" @click="cancelGeometryEdit" title="Cancel Changes"></v-btn>
    </div>

    <div class="map-overlay top-left-search">
      <SearchComponent/>
      
      <!-- Список геообъектов (Hierarchy) -->
      <v-card class="mt-2 feature-list-card" max-height="60vh">
        <GeoObjectTree />
      </v-card>
    </div>

    <!-- Диалог для ввода метаданных нового объекта -->
    <v-dialog v-model="metadataDialog" max-width="500px">
        <v-card>
            <v-card-title>New {{ drawingType }}</v-card-title>
            <v-card-text>
                <v-text-field v-model="newObjectMetadata.name" label="Name" required></v-text-field>
                <v-textarea v-model="newObjectMetadata.description" label="Description"></v-textarea>
                <v-select v-model="newObjectMetadata.status" :items="['COMPLETED', 'IN_PROCESS', 'REJECTED']" label="Status" required></v-select>
                
                <v-select
                  v-if="drawingType === 'Point'"
                  v-model="newObjectMetadata.type"
                  :items="['camera', 'pillar', 'other']"
                  label="Point Type"
                  required
                ></v-select>

                <template v-if="drawingType === 'Point' && newObjectMetadata.type === 'camera'">
                  <v-text-field v-model="newObjectCameraDetails.ip_address" label="Camera IP Address" required></v-text-field>
                  <v-text-field v-model="newObjectCameraDetails.port" label="Camera Port" type="number" required></v-text-field>
                  <v-text-field v-model="newObjectCameraDetails.login" label="Camera Login" required></v-text-field>
                  <v-text-field v-model="newObjectCameraDetails.password" label="Camera Password" type="password" required></v-text-field>
                </template>
            </v-card-text>
            <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn variant="text" @click="cancelNewFeature">Cancel</v-btn>
                <v-btn color="primary" @click="saveNewFeature">Save</v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>

    <CesiumSwipeDialog v-model="swipeActive" />

  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, computed, toRaw, shallowRef } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
// @ts-ignore
import CesiumMVTImageryProvider from 'cesium-mvt-imagery-provider';
import { debounce } from 'lodash';
import type { ImageryLayer, TerrainLayer, ProjectPoint, ProjectMultiline, ProjectPolygon, Status } from '@/types/api';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';
import GeodataService from '@/services/geodata.service';
import GeoObjectTree from './GeoObjectTree.vue';
import CesiumSwipeDialog from './CesiumSwipeDialog.vue';
import { parseStyle } from '@/util/style.util';
import { ensureMultiType } from '@/util/geo.util';
import { GeoJSON } from 'ol/format';
import { Polygon as OLPolygon } from 'ol/geom';
import * as turf from '@turf/turf';
import geoCalcService from '@/services/geo-calc.service';

const props = defineProps<{
  projectId: string;
}>();

const store = useStore();
const cesiumContainer = ref<HTMLElement | null>(null);
const cesiumParent = ref<HTMLElement | null>(null);
let viewer: Cesium.Viewer | null = null;
const geoJsonFormat = new GeoJSON();

// --- Состояние компонента ---
const drawMode = ref<'Point' | 'MultiLineString' | 'Polygon' | null>(null);
const measureMode = ref<'length' | 'area' | null>(null);
const isBufferMode = ref(false);
const bufferDistance = ref(100);
const bufferCenterCoords = ref<number[]>([0, 0]);
const bufferSourceEntity = ref<Cesium.Entity | null>(null);
const bufferPreviewEntity = ref<Cesium.Entity | null>(null);
let bufferHandler: Cesium.ScreenSpaceEventHandler | null = null;

const isGeometryEditMode = ref(false);
const swipeActive = ref(false);

// --- Measurement State ---
const measurePoints = ref<Cesium.Cartesian3[]>([]);
const measureEntities = ref<Cesium.Entity[]>([]);
const measureTooltips = ref<{ position: Cesium.Cartesian3, text: string }[]>([]);
let measureHandler: Cesium.ScreenSpaceEventHandler | null = null;

const stopActiveTool = () => {
  drawMode.value = null;
  measureMode.value = null;
  isBufferMode.value = false;
  clearMeasurements();
};

const clearMeasurements = () => {
  const v = viewer;
  if (!v) return;

  measureEntities.value.forEach(e => v.entities.remove(e));
  measureEntities.value = [];
  measurePoints.value = [];
  measureTooltips.value = [];

  if (bufferSourceEntity.value) {
    v.entities.remove(bufferSourceEntity.value);
    bufferSourceEntity.value = null;
  }
  if (bufferPreviewEntity.value) {
    v.entities.remove(bufferPreviewEntity.value);
    bufferPreviewEntity.value = null;
  }
  bufferCenterCoords.value = [0, 0];
};

const getTooltipStyle = (position: Cesium.Cartesian3) => {
  if (!viewer) return { display: 'none' };
  const canvasPosition = Cesium.SceneTransforms.worldToWindowCoordinates(viewer.scene, position);
  if (!canvasPosition) return { display: 'none' };
  
  return {
    left: canvasPosition.x + 'px',
    top: (canvasPosition.y - 15) + 'px',
    display: 'block'
  };
};

// --- Measurement Logic ---
watch(measureMode, (newMode) => {
  const v = viewer;
  if (!v) return;

  if (measureHandler) {
    measureHandler.destroy();
    measureHandler = null;
  }
  
  clearMeasurements();
  if (!newMode) return;

  isBufferMode.value = false;
  drawMode.value = null;

  measureHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
  
  measureHandler.setInputAction(async (click: any) => {
    const position = v.scene.pickPosition(click.position);
    if (Cesium.defined(position)) {
      measurePoints.value.push(position);
      
      // Add point entity
      const pointEntity = v.entities.add({
        position: position,
        point: {
          pixelSize: 8,
          color: Cesium.Color.YELLOW,
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        }
      });
      measureEntities.value.push(pointEntity);

      if (newMode === 'length' && measurePoints.value.length > 1) {
        const p1 = measurePoints.value[measurePoints.value.length - 2];
        const p2 = measurePoints.value[measurePoints.value.length - 1];
        
        const carto1 = Cesium.Cartographic.fromCartesian(p1);
        const carto2 = Cesium.Cartographic.fromCartesian(p2);
        
        const dist = geoCalcService.calculateDistance(
          [Cesium.Math.toDegrees(carto1.longitude), Cesium.Math.toDegrees(carto1.latitude), carto1.height],
          [Cesium.Math.toDegrees(carto2.longitude), Cesium.Math.toDegrees(carto2.latitude), carto2.height],
          true
        );

        measureTooltips.value.push({
          position: position,
          text: geoCalcService.formatDistance(dist)
        });
      }
    }
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

  // Preview Line/Polygon
  const previewEntity = v.entities.add({
    polyline: {
      positions: new Cesium.CallbackProperty(() => {
        if (measurePoints.value.length === 0) return [];
        return [...measurePoints.value];
      }, false),
      width: 2,
      material: new Cesium.PolylineDashMaterialProperty({ color: Cesium.Color.YELLOW }),
      clampToGround: true
    },
    polygon: newMode === 'area' ? {
      hierarchy: new Cesium.CallbackProperty(() => {
        if (measurePoints.value.length < 3) return new Cesium.PolygonHierarchy([]);
        return new Cesium.PolygonHierarchy(measurePoints.value);
      }, false),
      material: Cesium.Color.YELLOW.withAlpha(0.3)
    } : undefined
  });
  measureEntities.value.push(previewEntity);

  if (newMode === 'area') {
    measureHandler.setInputAction(async (movement: any) => {
        if (measurePoints.value.length < 2) return;
        
        const position = v.scene.pickPosition(movement.endPosition);
        if (Cesium.defined(position)) {
            // Dynamic area calculation could be added here
        }
    }, Cesium.ScreenSpaceEventType.MOUSE_MOVE);
  }

  measureHandler.setInputAction(async () => {
    if (newMode === 'area' && measurePoints.value.length >= 3) {
      const coords = await sampleHeights(measurePoints.value);
      const polygon = new OLPolygon([coords.map(c => [c[0], c[1]])]);
      const areaText = geoCalcService.formatArea(polygon);
      
      const center = Cesium.BoundingSphere.fromPoints(measurePoints.value).center;
      measureTooltips.value.push({
        position: center,
        text: `Total Area: ${areaText}`
      });
    }
    
    if (measureHandler) {
      measureHandler.destroy();
      measureHandler = null;
    }
  }, Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK);
});

// --- Buffer Logic ---
watch(isBufferMode, (active) => {
  const v = viewer;
  if (!v) return;

  if (bufferHandler) {
    bufferHandler.destroy();
    bufferHandler = null;
  }

  clearMeasurements();
  if (!active) return;

  measureMode.value = null;
  drawMode.value = null;

  bufferHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
  
  bufferHandler.setInputAction((click: any) => {
    const position = v.scene.pickPosition(click.position);
    if (Cesium.defined(position)) {
      const carto = Cesium.Cartographic.fromCartesian(position);
      bufferCenterCoords.value = [Cesium.Math.toDegrees(carto.longitude), Cesium.Math.toDegrees(carto.latitude)];
      
      if (bufferSourceEntity.value) v.entities.remove(bufferSourceEntity.value);
      
      bufferSourceEntity.value = v.entities.add({
        position: position,
        point: {
          pixelSize: 10,
          color: Cesium.Color.BLUE,
          outlineColor: Cesium.Color.WHITE,
          outlineWidth: 2,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        }
      });

      updateBufferPreview();
    }
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);
});

const updateBufferPreview = () => {
  const v = viewer;
  if (!v || !bufferCenterCoords.value[0]) return;

  if (bufferPreviewEntity.value) v.entities.remove(bufferPreviewEntity.value);

  const center = bufferCenterCoords.value;
  const radius = bufferDistance.value;

  bufferPreviewEntity.value = v.entities.add({
    position: Cesium.Cartesian3.fromDegrees(center[0], center[1]),
    ellipse: {
      semiMinorAxis: radius,
      semiMajorAxis: radius,
      material: Cesium.Color.BLUE.withAlpha(0.2),
      outline: true,
      outlineColor: Cesium.Color.BLUE,
      outlineWidth: 2,
      heightReference: Cesium.HeightReference.RELATIVE_TO_GROUND
    }
  });
};

watch(bufferDistance, updateBufferPreview);

// --- Shot Frame Editing ---
const SHOT_FRAME_MIN_ZOOM = Number(import.meta.env.VITE_SHOT_FRAME_MIN_ZOOM || 16);
const isZoomHighEnough = ref(false);
const modifiedSubIds = ref(new Set<number>());
const isLoadingParts = ref(false);
const cacheBuster = ref(Date.now());

const visibleLayerIds = ref<string[]>([]);
const activeImageryLayers = shallowRef<Record<string, Cesium.ImageryLayer>>({});
const layerOpacities = ref<Record<string, number>>({});
const imageryMenuOpen = ref(false);
const terrainMenuOpen = ref(false);
const autoExtentEnabled = ref(false);

// --- Состояние импорта ---
const importFileDialog = ref(false);
const importFile = ref<File | null>(null);
const isImporting = ref(false);

// --- Состояние для нового объекта ---
const metadataDialog = ref(false);
const drawingType = ref('');
const newObjectGeometry = ref<any>(null);
const newObjectMetadata = ref({
  name: '',
  description: '',
  status: 'IN_PROCESS' as Status,
  type: 'other',
  characteristics: {} as Record<string, any>
});
const newObjectCameraDetails = ref({
  ip_address: '',
  port: 554,
  login: '',
  password: '',
});

// --- Данные из Vuex ---
const imageryLayers = computed<ImageryLayer[]>(() => store.state.geodata.imageryLayers?.content || []);
const terrainLayers = computed<TerrainLayer[]>(() => store.state.geodata.terrainLayers?.content || []);
const selectedTerrainId = ref<string | null>(null);

const points = computed<ProjectPoint[]>(() => store.state.geodata.points);
const multilines = computed<ProjectMultiline[]>(() => store.state.geodata.multilines);
const polygons = computed<ProjectPolygon[]>(() => store.state.geodata.polygons);
const selectedFeatureId = computed(() => store.state.geodata.selectedFeatureId);

const hiddenFeatureIds = computed(() => {
  const hidden = new Set<string>();
  points.value.forEach(p => { if (p.characteristics?.visible === false) hidden.add(p.id); });
  multilines.value.forEach(l => { if (l.characteristics?.visible === false) hidden.add(l.id); });
  polygons.value.forEach(p => { if (p.characteristics?.visible === false) hidden.add(p.id); });
  return hidden;
});

const selectedFeature = computed(() => {
  if (!selectedFeatureId.value) return null;
  const p = points.value.find(f => f.id === selectedFeatureId.value);
  if (p) return { ...p, type: 'Point' };
  const m = multilines.value.find(f => f.id === selectedFeatureId.value);
  if (m) return { ...m, type: 'MultiLineString' };
  const poly = polygons.value.find(f => f.id === selectedFeatureId.value);
  if (poly) return { ...poly, type: 'Polygon' };
  return null;
});

// --- Стилизация MVT для Cesium ---
const cesiumMvtStyleFunction = (feature: any) => {
  // Пытаемся распарсить характеристики для кастомных стилей
  const charProp = feature.properties.characteristics;
  let characteristics = charProp;
  if (typeof charProp === 'string' && charProp.startsWith('{')) {
    try {
      characteristics = JSON.parse(charProp);
    } catch (e) {
      characteristics = feature.properties;
    }
  } else if (!characteristics) {
    characteristics = feature.properties;
  }

  const style = characteristics?.style || {};
  
  // Возвращаем стиль в формате Canvas (strokeStyle, fillStyle)
  return {
    strokeStyle: style.line?.color || '#00FF00', // Зеленый для линий
    fillStyle: style.poly?.fillColor || 'rgba(0, 255, 0, 0.3)', // Полупрозрачный зеленый для полигонов
    lineWidth: style.line?.width || 2
  };
};

const selectAndZoomToFeature = (id: string) => {
  store.dispatch('geodata/selectFeature', id);
  const v = viewer;
  if (!v) return;
  const entity = v.entities.getById(id);
  if (entity) {
    v.zoomTo(entity);
  }
};

// --- MVT Векторные слои тайлов для Cesium ---
let pointsMvtLayer: Cesium.ImageryLayer | null = null;
let linesMvtLayer: Cesium.ImageryLayer | null = null;
let polygonsMvtLayer: Cesium.ImageryLayer | null = null;

const initMvtLayers = (projectId: string) => {
  const v = viewer;
  if (!v) return;

  // Удаляем старые слои
  if (pointsMvtLayer) v.imageryLayers.remove(pointsMvtLayer);
  if (linesMvtLayer) v.imageryLayers.remove(linesMvtLayer);
  if (polygonsMvtLayer) v.imageryLayers.remove(polygonsMvtLayer);

  const baseUrl = window.location.origin;
  const mvtOptions = {
    onRenderFeature: (feature: any) => {
      // 1. Скрываем объект, если он редактируется
      if (isGeometryEditMode.value && feature.properties.id === selectedFeatureId.value) {
        return false;
      }

      // 2. Мгновенный фильтр видимости на основе состояния Vuex
      if (feature.properties.id && hiddenFeatureIds.value.has(feature.properties.id)) {
        return false;
      }

      // Пытаемся распарсить характеристики для кастомных стилей
      const charProp = feature.properties.characteristics;
      let characteristics = charProp;
      if (typeof charProp === 'string' && charProp.startsWith('{')) {
        try {
          characteristics = JSON.parse(charProp);
        } catch (e) {
          characteristics = feature.properties;
        }
      } else if (!characteristics) {
        characteristics = feature.properties;
      }

      // 3. Проверка видимости из данных самого тайла (как запасной вариант)
      const isVisibleInTile = characteristics?.visible !== false && characteristics?.isVisible !== false;
      if (!isVisibleInTile) {
        return false;
      }

      return true;
    },
    style: cesiumMvtStyleFunction,
    tilingScheme: new Cesium.WebMercatorTilingScheme(),
    tileWidth: 512,
    tileHeight: 512,
    onSelectFeature: (feature: any) => {
        if (feature && feature.properties.id) {
            store.dispatch('geodata/selectFeature', feature.properties.id);
        }
    }
  };

  polygonsMvtLayer = v.imageryLayers.addImageryProvider(new CesiumMVTImageryProvider({
    ...mvtOptions,
    urlTemplate: `${baseUrl}/tiles/geodata.mvt_project_polygons/{z}/{x}/{y}.pbf?project_id_param=${projectId}&t=${cacheBuster.value}`,
    layerName: 'geodata.mvt_project_polygons'
  }));

  linesMvtLayer = v.imageryLayers.addImageryProvider(new CesiumMVTImageryProvider({
    ...mvtOptions,
    urlTemplate: `${baseUrl}/tiles/geodata.mvt_project_multilines/{z}/{x}/{y}.pbf?project_id_param=${projectId}&t=${cacheBuster.value}`,
    layerName: 'geodata.mvt_project_multilines'
  }));

  pointsMvtLayer = v.imageryLayers.addImageryProvider(new CesiumMVTImageryProvider({
    ...mvtOptions,
    urlTemplate: `${baseUrl}/tiles/geodata.mvt_project_points/{z}/{x}/{y}.pbf?project_id_param=${projectId}&t=${cacheBuster.value}`,
    layerName: 'geodata.mvt_project_points'
  }));

  raiseMvtLayersToTop();
};

const raiseMvtLayersToTop = () => {
  const v = viewer;
  if (!v) return;
  if (polygonsMvtLayer && v.imageryLayers.contains(polygonsMvtLayer)) {
    v.imageryLayers.raiseToTop(polygonsMvtLayer);
  }
  if (linesMvtLayer && v.imageryLayers.contains(linesMvtLayer)) {
    v.imageryLayers.raiseToTop(linesMvtLayer);
  }
  if (pointsMvtLayer && v.imageryLayers.contains(pointsMvtLayer)) {
    v.imageryLayers.raiseToTop(pointsMvtLayer);
  }
};

const refreshMvtSources = () => {
  cacheBuster.value = Date.now();
  if (props.projectId) {
    initMvtLayers(props.projectId);
  }
};

// --- Вспомогательные функции ---

const clearImageryLayers = () => {
  const v = viewer;
  if (!v) return;
  for (const id in activeImageryLayers.value) {
    v.imageryLayers.remove(toRaw(activeImageryLayers.value[id]));
  }
  activeImageryLayers.value = {};
  visibleLayerIds.value = [];
  layerOpacities.value = {};
};

const toggleImageryLayer = (layerInfo: ImageryLayer) => {
  const v = viewer;
  if (!v) return;
  const isVisible = visibleLayerIds.value.includes(layerInfo.id);

  if (isVisible) {
    // Avoid duplicate adding
    if (activeImageryLayers.value[layerInfo.id]) return;

    const provider = new Cesium.WebMapServiceImageryProvider({
      url: layerInfo.serviceUrl,
      layers: layerInfo.workspace + ":" + layerInfo.layerName,
      parameters: {
        transparent: 'true',
        format: 'image/png',
      },
    });
    const layer = v.imageryLayers.addImageryProvider(provider);
    layer.alpha = (layerOpacities.value[layerInfo.id] || 100) / 100;
    
    activeImageryLayers.value = {
      ...activeImageryLayers.value,
      [layerInfo.id]: layer
    };

    if (layerOpacities.value[layerInfo.id] === undefined) {
      layerOpacities.value[layerInfo.id] = 100;
    }

    raiseMvtLayersToTop();
  } else {
    const layer = activeImageryLayers.value[layerInfo.id];
    if (layer) {
      v.imageryLayers.remove(toRaw(layer));
      const nextLayers = { ...activeImageryLayers.value };
      delete nextLayers[layerInfo.id];
      activeImageryLayers.value = nextLayers;
    }
  }
};

const setLayerOpacity = (layerId: string, opacity: number) => {
  const layer = activeImageryLayers.value[layerId];
  if (layer) {
    layer.alpha = opacity / 100;
    layerOpacities.value[layerId] = opacity;
  }
};

const zoomToExtent = () => {
  const v = viewer;
  if (!v || v.entities.values.length === 0) return;
  v.zoomTo(v.entities);
};

const openImportFileDialog = () => {
  importFile.value = null;
  importFileDialog.value = true;
};

const executeFileImport = async () => {
  if (!importFile.value || !props.projectId) return;
  isImporting.value = true;
  try {
    await GeodataService.importFileToProject(props.projectId, importFile.value);
    await store.dispatch('geodata/fetchVectorDataForProject', props.projectId);
    importFileDialog.value = false;
  } catch (error) {
    console.error("Import failed:", error);
  } finally {
    isImporting.value = false;
  }
};

const hasZHeight = (geom: any): boolean => {
  if (!geom || !geom.coordinates) return false;
  const coords = geom.coordinates;
  
  const checkArray = (arr: any[]): boolean => {
    if (arr.length === 0) return false;
    if (typeof arr[0] === 'number') {
      return arr.length >= 3 && arr[2] !== 0;
    }
    return arr.some(item => Array.isArray(item) && checkArray(item));
  };

  return checkArray(coords);
};

const getFlattened = (coords: any[], includeZ: boolean) => {
  const flat: number[] = [];
  coords.forEach(p => {
    flat.push(p[0]);
    flat.push(p[1]);
    if (includeZ) flat.push(p[2] || 0);
  });
  return flat;
};

const createEntitiesFromGeoJSON = (v: Cesium.Viewer, geom: any, options: any) => {
  const is3D = hasZHeight(geom);
  const style = options.style || {};
  const isVisible = options.show !== false;

  const entities: Cesium.Entity[] = [];

  if (geom.type === 'Point' || geom.type === 'MultiPoint') {
    const allCoords = geom.type === 'MultiPoint' ? geom.coordinates : [geom.coordinates];
    allCoords.forEach((coords: any, idx: number) => {
      if (!coords || coords.length < 2) return;
      let position = is3D 
        ? Cesium.Cartesian3.fromDegrees(coords[0], coords[1], coords[2])
        : Cesium.Cartesian3.fromDegrees(coords[0], coords[1]);
      
      const heightReference = is3D ? Cesium.HeightReference.NONE : Cesium.HeightReference.RELATIVE_TO_GROUND;
      const eOptions = { 
        ...options, 
        id: allCoords.length > 1 ? `${options.id}-${idx}` : options.id, 
        position 
      };

      if (style.icon?.url) {
        entities.push(v.entities.add({
          ...eOptions,
          billboard: {
            image: style.icon.url,
            scale: style.icon.scale || 1.0,
            heightReference: heightReference,
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
          }
        }));
      } else {
        entities.push(v.entities.add({
          ...eOptions,
          point: {
            pixelSize: 10,
            color: Cesium.Color.fromCssColorString(style.poly?.fillColor || '#3399CC'),
            outlineColor: Cesium.Color.WHITE,
            outlineWidth: 2,
            heightReference: heightReference,
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
          }
        }));
      }
    });
  } else if (geom.type === 'MultiLineString' || geom.type === 'LineString') {
    const allLineCoords = geom.type === 'MultiLineString' ? geom.coordinates : [geom.coordinates];
    allLineCoords.forEach((lineCoords: any, idx: number) => {
      if (lineCoords && lineCoords.length >= 2) {
        const flattened = getFlattened(lineCoords, is3D);
        const positions = is3D 
          ? Cesium.Cartesian3.fromDegreesArrayHeights(flattened)
          : Cesium.Cartesian3.fromDegreesArray(flattened);

        entities.push(v.entities.add({
          ...options,
          id: allLineCoords.length > 1 ? `${options.id}-${idx}` : options.id,
          polyline: {
            positions: positions,
            width: style.line?.width || 2,
            material: Cesium.Color.fromCssColorString(style.line?.color || '#3399CC'),
            clampToGround: !is3D,
          }
        }));
      }
    });
  } else if (geom.type === 'Polygon' || geom.type === 'MultiPolygon') {
    const allPolygons = geom.type === 'MultiPolygon' ? geom.coordinates : [geom.coordinates];
    allPolygons.forEach((polygonCoords: any, idx: number) => {
      const outerRing = polygonCoords[0];
      if (outerRing && outerRing.length >= 3) {
        const flattened = getFlattened(outerRing, is3D);
        const positions = is3D
          ? Cesium.Cartesian3.fromDegreesArrayHeights(flattened)
          : Cesium.Cartesian3.fromDegreesArray(flattened);

        const holes = [];
        if (polygonCoords.length > 1) {
          for (let i = 1; i < polygonCoords.length; i++) {
            const holeFlattened = getFlattened(polygonCoords[i], is3D);
            const holePositions = is3D
              ? Cesium.Cartesian3.fromDegreesArrayHeights(holeFlattened)
              : Cesium.Cartesian3.fromDegreesArray(holeFlattened);
            holes.push(new Cesium.PolygonHierarchy(holePositions));
          }
        }

        const polyId = allPolygons.length > 1 ? `${options.id}-${idx}` : options.id;

        entities.push(v.entities.add({
          ...options,
          id: polyId,
          polygon: {
            hierarchy: new Cesium.PolygonHierarchy(positions, holes),
            material: Cesium.Color.fromCssColorString(style.poly?.fillColor || 'rgba(51, 153, 204, 0.4)'),
            heightReference: is3D ? Cesium.HeightReference.NONE : Cesium.HeightReference.RELATIVE_TO_GROUND,
            perPositionHeight: is3D,
          }
        }));
        
        entities.push(v.entities.add({
          id: `${polyId}-outline`,
          name: options.name,
          show: isVisible,
          polyline: {
            positions: [...positions, positions[0]],
            width: (style.line?.width || 2) + 1,
            material: Cesium.Color.fromCssColorString(style.line?.color || '#3399CC'),
            clampToGround: !is3D,
          }
        }));
      }
    });
  }
  return entities;
};

const saveNewFeature = async () => {
  if (!newObjectGeometry.value || !props.projectId) return;

  let characteristics = { type: newObjectMetadata.value.type };
  if (newObjectMetadata.value.type === 'camera') {
    characteristics = { ...characteristics, ...newObjectCameraDetails.value };
  }

  const payload = {
    projectId: props.projectId,
    geom: ensureMultiType(newObjectGeometry.value),
    name: newObjectMetadata.value.name,
    description: newObjectMetadata.value.description,
    status: newObjectMetadata.value.status,
    characteristics: characteristics,
  };

  await store.dispatch('geodata/createFeature', { type: drawingType.value, data: payload });
  metadataDialog.value = false;
  cancelNewFeature();
};

const cancelNewFeature = () => {
  metadataDialog.value = false;
  newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status, type: 'other', characteristics: {} };
  newObjectCameraDetails.value = { ip_address: '', port: 8000, login: '', password: '' };
};

const editPoints = ref<{subId: number, points: Cesium.Cartesian3[]}[]>([]);
const draggerEntities = ref<Cesium.Entity[]>([]);
let activeDragger: Cesium.Entity | null = null;
let editHandler: Cesium.ScreenSpaceEventHandler | null = null;

const addDraggersForEntity = (v: Cesium.Viewer, entity: Cesium.Entity, subId: number) => {
    let positions: Cesium.Cartesian3[] = [];
    if (entity.position) {
        const pos = entity.position.getValue(Cesium.JulianDate.now());
        if (pos) positions = [pos];
    } else if (entity.polyline?.positions) {
        const pos = entity.polyline.positions.getValue(Cesium.JulianDate.now()) as Cesium.Cartesian3[];
        if (pos) positions = [...pos];
    } else if (entity.polygon?.hierarchy) {
        const hierarchy = entity.polygon.hierarchy.getValue(Cesium.JulianDate.now()) as Cesium.PolygonHierarchy;
        if (hierarchy) positions = [...hierarchy.positions];
    }

    positions.forEach((pos, index) => {
        const dragger = v.entities.add({
            position: new Cesium.CallbackPositionProperty(() => {
                const group = editPoints.value.find(g => g.subId === subId);
                return group?.points[index] || pos;
            }, false) as any,
            point: {
                pixelSize: 12,
                color: Cesium.Color.RED,
                outlineColor: Cesium.Color.WHITE,
                outlineWidth: 2,
                disableDepthTestDistance: Number.POSITIVE_INFINITY,
            },
        });
        (dragger as any).userData = { subId, index };
        draggerEntities.value.push(dragger);
    });
    
    // Add to our reactive edit state if not present
    let group = editPoints.value.find(g => g.subId === subId);
    if (!group) {
        group = { subId, points: [...positions] };
        editPoints.value.push(group);
    }
};

const fetchParts = debounce(async () => {
  if (!isGeometryEditMode.value || !isZoomHighEnough.value || !selectedFeatureId.value || !selectedFeature.value) return;

  const v = viewer;
  if (!v) return;
  
  const rect = v.camera.computeViewRectangle();
  if (!rect) return;

  const minX = Cesium.Math.toDegrees(rect.west);
  const minY = Cesium.Math.toDegrees(rect.south);
  const maxX = Cesium.Math.toDegrees(rect.east);
  const maxY = Cesium.Math.toDegrees(rect.north);

  const typeMap: Record<string, 'points' | 'multilines' | 'polygons'> = {
    'Point': 'points',
    'MultiLineString': 'multilines',
    'Polygon': 'polygons'
  };

  const apiType = typeMap[selectedFeature.value.type];
  if (!apiType) return;

  isLoadingParts.value = true;
  try {
    const response = await GeodataService.getGeometryParts(
      apiType,
      selectedFeatureId.value,
      minX, minY, maxX, maxY
    );
    
    const parts = response.data;
    
    parts.forEach((part: any) => {
        const partId = `edit_${selectedFeatureId.value}_${part.subId}`;
        if (v.entities.getById(partId)) return;

        const geom = JSON.parse(part.geojson);
        const createdEntities = createEntitiesFromGeoJSON(v, geom, {
            id: partId,
            name: `${selectedFeature.value?.name} (Part ${part.subId})`,
            sub_id: part.subId,
            show: true,
            style: selectedFeature.value?.characteristics?.style
        });
        
        createdEntities.forEach(entity => {
            if (!entity.id.endsWith('-outline')) {
                addDraggersForEntity(v, entity, part.subId);
            }
        });
    });

  } catch (err) {
    console.error("Failed to fetch geometry parts:", err);
  } finally {
    isLoadingParts.value = false;
  }
}, 1000);

const handleCameraMoveForShotFrame = () => {
    if (!isGeometryEditMode.value || !viewer) return;
    const height = viewer.camera.positionCartographic.height;
    isZoomHighEnough.value = height < 5000; 
    
    if (isZoomHighEnough.value) {
        fetchParts();
    }
};

const sampleHeights = async (cartesianPoints: Cesium.Cartesian3[]) => {
  const v = viewer;
  if (!v) return cartesianPoints.map(p => {
    const carto = Cesium.Cartographic.fromCartesian(p);
    return [Cesium.Math.toDegrees(carto.longitude), Cesium.Math.toDegrees(carto.latitude), carto.height];
  });

  const cartographics = cartesianPoints.map(p => Cesium.Cartographic.fromCartesian(p));
  
  if (v.terrainProvider && !(v.terrainProvider instanceof Cesium.EllipsoidTerrainProvider)) {
    try {
      const sampled = await Cesium.sampleTerrainMostDetailed(v.terrainProvider, cartographics);
      return sampled.map(c => [
        Cesium.Math.toDegrees(c.longitude),
        Cesium.Math.toDegrees(c.latitude),
        c.height
      ]);
    } catch (e) {
      console.warn("Terrain sampling failed, using pickPosition height", e);
    }
  }

  return cartographics.map(c => [
    Cesium.Math.toDegrees(c.longitude),
    Cesium.Math.toDegrees(c.latitude),
    c.height
  ]);
};

const sampleHeightsForEditPoints = async () => {
    const results: {subId: number, coords: number[][]}[] = [];
    for (const group of editPoints.value) {
        if (modifiedSubIds.value.has(group.subId)) {
            const sampled = await sampleHeights(group.points);
            results.push({ subId: group.subId, coords: sampled });
        }
    }
    return results;
};

const enterGeometryEditMode = () => {
  const v = viewer;
  if (!v || !selectedFeature.value) return;

  const height = v.camera.positionCartographic.height;
  isZoomHighEnough.value = height < 5000;

  if (!isZoomHighEnough.value) {
      alert(`Please zoom in closer to edit this complex object in 3D.`);
      return;
  }

  isGeometryEditMode.value = true;
  editPoints.value = [];
  draggerEntities.value = [];
  modifiedSubIds.value.clear();

  // Refresh MVT to hide selected object
  refreshMvtSources();

  editHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
  
  editHandler.setInputAction((click: any) => {
    const picked = v.scene.pick(click.position);
    if (Cesium.defined(picked) && picked.id && draggerEntities.value.includes(picked.id)) {
      activeDragger = picked.id;
      v.scene.screenSpaceCameraController.enableRotate = false;
    }
  }, Cesium.ScreenSpaceEventType.LEFT_DOWN);

  editHandler.setInputAction((movement: any) => {
    if (activeDragger) {
      const position = v.scene.pickPosition(movement.endPosition);
      if (Cesium.defined(position)) {
        const { subId, index } = (activeDragger as any).userData;
        const group = editPoints.value.find(g => g.subId === subId);
        if (group) {
            group.points[index] = position;
            modifiedSubIds.value.add(subId);
        }
      }
    }
  }, Cesium.ScreenSpaceEventType.MOUSE_MOVE);

  editHandler.setInputAction(() => {
    activeDragger = null;
    v.scene.screenSpaceCameraController.enableRotate = true;
  }, Cesium.ScreenSpaceEventType.LEFT_UP);

  v.camera.moveEnd.addEventListener(handleCameraMoveForShotFrame);
  fetchParts();
};

const exitEditMode = () => {
  const v = viewer;
  if (!v) return;
  
  if (editHandler) {
    editHandler.destroy();
    editHandler = null;
  }
  
  // Remove all edit-related entities
  draggerEntities.value.forEach(e => v.entities.remove(e));
  draggerEntities.value = [];
  
  // Remove all part entities
  const editEntities = v.entities.values.filter(e => e.id.startsWith('edit_'));
  editEntities.forEach(e => v.entities.remove(e));
  
  v.camera.moveEnd.removeEventListener(handleCameraMoveForShotFrame);
  
  isGeometryEditMode.value = false;
  modifiedSubIds.value.clear();
  editPoints.value = [];
  
  refreshMvtSources();
};

const confirmGeometryEdit = async () => {
  if (!selectedFeature.value || !selectedFeatureId.value) return;

  const typeMap: Record<string, 'points' | 'multilines' | 'polygons'> = {
    'Point': 'points',
    'MultiLineString': 'multilines',
    'Polygon': 'polygons'
  };
  const apiType = typeMap[selectedFeature.value.type];

  if (modifiedSubIds.value.size > 0) {
    const updatedPartsData = await sampleHeightsForEditPoints();
    const partsToUpdate = updatedPartsData.map(partData => {
        let geojson;
        if (selectedFeature.value?.type === 'Point') {
            geojson = JSON.stringify({ type: 'Point', coordinates: partData.coords[0] });
        } else if (selectedFeature.value?.type === 'MultiLineString') {
            geojson = JSON.stringify({ type: 'LineString', coordinates: partData.coords });
        } else {
            geojson = JSON.stringify({ type: 'Polygon', coordinates: [[...partData.coords, partData.coords[0]]] });
        }
        return { subId: partData.subId, geojson };
    });

    if (partsToUpdate.length > 0) {
        await GeodataService.updateGeometryParts(apiType, selectedFeatureId.value, partsToUpdate);
    }
  }

  exitEditMode();
  
  if (props.projectId) {
    store.dispatch('geodata/fetchVectorSummaryForProject', props.projectId);
  }
};

const cancelGeometryEdit = () => {
  exitEditMode();
};

// --- Watchers ---

watch(() => props.projectId, (newId) => {
  if (newId) {
    clearImageryLayers();
    store.commit('geodata/SET_SELECTED_PROJECT_ID', newId);
    
    // Инициализируем MVT слои для нового проекта
    initMvtLayers(newId);

    store.dispatch('geodata/fetchVectorSummaryForProject', newId);
    store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 });
    store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
  }
}, { immediate: true });

watch(selectedTerrainId, async (newId) => {
  const v = viewer;
  if (!v) return;
  if (!newId) {
    v.terrainProvider = new Cesium.EllipsoidTerrainProvider();
  } else {
    const layer = terrainLayers.value.find(l => l.id === newId);
    if (layer) {
      v.terrainProvider = await Cesium.CesiumTerrainProvider.fromUrl(layer.terrainUrl);
    }
  }
});

watch([points, multilines, polygons], refreshMvtSources);

// При изменении видимости объектов принудительно обновляем MVT слои
watch(hiddenFeatureIds, () => {
  refreshMvtSources();
}, { deep: true });

watch(selectedFeatureId, (newId) => {
  const v = viewer;
  if (!newId || !v || !autoExtentEnabled.value || !store.state.geodata.lastSelectionShouldZoom) return;
  
  let entity = v.entities.getById(newId);
  if (!entity) {
    // If not found (e.g. for Multi-parts where first part might be id-0), find any that starts with the id
    entity = v.entities.values.find(e => e.id.startsWith(newId));
  }

  if (entity) {
    v.zoomTo(entity);
  }
});

const drawPoints = ref<Cesium.Cartesian3[]>([]);
let temporaryEntity: Cesium.Entity | null = null;
let drawingHandler: Cesium.ScreenSpaceEventHandler | null = null;

watch(drawMode, (newMode) => {
  const v = viewer;
  if (!v) return;

  if (drawingHandler) {
    drawingHandler.destroy();
    drawingHandler = null;
  }
  if (temporaryEntity) {
    v.entities.remove(temporaryEntity);
    temporaryEntity = null;
  }
  drawPoints.value = [];

  if (!newMode) return;

  drawingHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
  drawingType.value = newMode;

  if (newMode === 'Point') {
    drawingHandler.setInputAction(async (click: any) => {
      const position = v.scene.pickPosition(click.position);
      if (Cesium.defined(position)) {
        const coords = await sampleHeights([position]);
        newObjectGeometry.value = {
          type: 'Point',
          coordinates: coords[0]
        };
        newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status, type: 'other', characteristics: {} };
        metadataDialog.value = true;
        drawMode.value = null;
      }
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK);
  } else {
    drawingHandler.setInputAction((click: any) => {
      const position = v.scene.pickPosition(click.position);
      if (Cesium.defined(position)) {
        drawPoints.value.push(position);
        
        if (!temporaryEntity) {
          if (newMode === 'MultiLineString') {
            temporaryEntity = v.entities.add({
              polyline: {
                positions: new Cesium.CallbackProperty(() => drawPoints.value, false),
                width: 3,
                material: Cesium.Color.YELLOW,
                clampToGround: true
              }
            });
          } else {
            temporaryEntity = v.entities.add({
              polygon: {
                hierarchy: new Cesium.CallbackProperty(() => new Cesium.PolygonHierarchy(drawPoints.value), false),
                material: Cesium.Color.YELLOW.withAlpha(0.5)
              },
              polyline: {
                positions: new Cesium.CallbackProperty(() => [...drawPoints.value, drawPoints.value[0]], false),
                width: 2,
                material: Cesium.Color.YELLOW,
                clampToGround: true
              }
            });
          }
        }
      }
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

    drawingHandler.setInputAction(async () => {
      if (drawPoints.value.length < 2) return;

      const coords = await sampleHeights(drawPoints.value);

      if (newMode === 'MultiLineString') {
        newObjectGeometry.value = { type: 'MultiLineString', coordinates: [coords] };
      } else {
        newObjectGeometry.value = { type: 'Polygon', coordinates: [[...coords, coords[0]]] };
      }

      newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status, type: 'other', characteristics: {} };
      metadataDialog.value = true;
      drawMode.value = null;
    }, Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK);
  }
});

// --- Lifecycle Hooks ---

onMounted(async () => {
  if (!cesiumContainer.value) return;

  const v = new Cesium.Viewer(cesiumContainer.value, {
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    baseLayer: new Cesium.ImageryLayer(new Cesium.OpenStreetMapImageryProvider({
      url: 'https://a.tile.openstreetmap.org/'
    })),
    animation: false,
    timeline: false,
    baseLayerPicker: false,
    navigationHelpButton: false,
    homeButton: true,
    geocoder: false,
    sceneModePicker: true,
    selectionIndicator: false,
    infoBox: false,
  });
  viewer = v;

  v.scene.globe.depthTestAgainstTerrain = true;

  v.screenSpaceEventHandler.setInputAction((click: any) => {
    const pickedObject = v.scene.pick(click.position);
    if (Cesium.defined(pickedObject) && pickedObject.id && pickedObject.id.id) {
        let actualId = pickedObject.id.id;
        // Strip suffixes like -outline or -0, -1 to get the base object ID
        if (actualId.endsWith('-outline')) {
            actualId = actualId.replace('-outline', '');
        }
        // Match -0, -1, -2 etc at the end
        const partMatch = actualId.match(/(.*)-[0-9]+$/);
        if (partMatch) {
            actualId = partMatch[1];
        }
        store.dispatch('geodata/selectFeature', actualId);
    } else {
        store.dispatch('geodata/selectFeature', null);
    }
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

  if (props.projectId) {
    initMvtLayers(props.projectId);
  }
});

onUnmounted(() => {
  const v = viewer;
  if (v) {
    v.destroy();
    viewer = null;
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

.cesium-viewer {
  width: 100%;
  height: 100%;
}

.map-overlay {
  position: absolute;
  background-color: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  padding: 8px;
  z-index: 1000;
}

.top-right-layers {
  top: 40px;
  right: 10px;
  background-color: transparent !important;
  padding: 0;
  box-shadow: none !important;
}

.top-left-search {
  top: 60px;
  left: 10px;
  width: 350px;
  display: flex;
  flex-direction: column;
  background-color: transparent !important;
}

.feature-list-card {
    overflow-y: auto;
    background-color: rgba(255, 255, 255, 0.9);
}

.top-right-details {
    top: 40px;
    right: 10px;
    width: 400px;
    max-height: calc(100% - 20px);
    overflow-y: auto;
    z-index: 1001;
}

.bottom-right {
  bottom: 20px;
  right: 10px;
  background-color: transparent !important;
}

.bottom-right-edit {
  bottom: 80px;
  right: 10px;
  background-color: transparent !important;
}

.shot-frame-overlay {
  position: absolute;
  top: 15px;
  left: 15px;
  right: 15px;
  bottom: 15px;
  border: 2px dashed #ff5252;
  background-color: rgba(255, 82, 82, 0.05);
  pointer-events: none;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shot-frame-label {
  background-color: rgba(255, 82, 82, 0.8);
  color: white;
  padding: 4px 12px;
  border-radius: 4px;
  font-weight: bold;
  font-size: 12px;
  letter-spacing: 1px;
}

.cesium-tooltip {
  position: absolute;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
  z-index: 1002;
  white-space: nowrap;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.buffer-panel {
  bottom: 20px;
  right: 60px;
  width: 220px;
}
</style>
