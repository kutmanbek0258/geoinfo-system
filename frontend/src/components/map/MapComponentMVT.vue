<template>
  <div ref="mapParent" class="map-container">
    <!-- Карта OpenLayers -->
    <div id="map" ref="mapContainer" class="map"></div>

    <!-- Оверлей 1: Переключатель слоев с выпадающим списком -->
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

      <!-- Кнопка печати -->
      <PrintDialog :map="map" />
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
            @click="toggleBufferMode"
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

      <v-btn
        icon="mdi-compare"
        color="white"
        class="mb-2"
        elevation="2"
        @click="swipeMapVisible = true"
        title="Swipe Tool (Compare Layers)"
      >
        <v-icon color="primary">mdi-compare</v-icon>
      </v-btn>

    <!-- Оверлей 6: Панель настройки буфера -->
    <v-fade-transition>
      <div v-if="isBufferMode" class="map-overlay buffer-panel">
        <div class="d-flex align-center justify-space-between mb-2">
          <span class="text-subtitle-2 font-weight-bold">Buffer Zone</span>
          <v-btn icon="mdi-close" size="x-small" variant="text" @click="isBufferMode = false"></v-btn>
        </div>
        
        <div v-if="!bufferSourceFeature" class="text-caption text-grey mb-2">
          Click on a map object to create a buffer
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
          <div class="text-caption text-grey text-center mt-1">
            Drag mouse to change radius
          </div>
        </template>
      </div>
    </v-fade-transition>
      
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

    <!-- Оверлей Shot Frame (Визуальная рамка для редактирования) -->
    <v-fade-transition>
      <div v-if="isGeometryEditMode && isZoomHighEnough" class="shot-frame-overlay">
        <div class="shot-frame-content">
          <div class="shot-frame-label">SHOT FRAME ACTIVE</div>
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
    <div v-if="selectedFeatureId && !isGeometryEditMode" class="map-overlay top-right-details">
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
      <!-- Search -->
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
                
                <!-- Point Type Selection (only for Point drawing) -->
                <v-select
                  v-if="drawingType === 'Point'"
                  v-model="newObjectMetadata.type"
                  :items="pointTypes"
                  label="Point Type"
                  required
                ></v-select>

                <!-- Camera Details (conditional) -->
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

    <SwipeMapDialog v-model="swipeMapVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed, toRaw, shallowRef } from 'vue';
import { debounce } from 'lodash';
import PrintDialog from '@/components/print/PrintDialog.vue';
import { useStore } from 'vuex';
import 'ol/ol.css';
import { Map, View, Feature } from 'ol';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import TileWMS from 'ol/source/TileWMS.js';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import MVT from 'ol/format/MVT';
import { Draw, Modify, Snap } from 'ol/interaction';
import { Collection, Overlay } from 'ol';
import { createEmpty, extend } from 'ol/extent';
import { Style, Stroke, Fill, Circle as CircleStyle } from 'ol/style';
import type { ImageryLayer, TerrainLayer, ProjectPoint, ProjectMultiline, ProjectPolygon, Status } from '@/types/api';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';
import { FullScreen } from 'ol/control';
import GeodataService from '@/services/geodata.service';
import geoCalcService from '@/services/geo-calc.service';
import GeoObjectTree from './GeoObjectTree.vue';
import { parseStyle } from '@/util/style.util';
import { ensureMultiType } from '@/util/geo.util';
import SwipeMapDialog from './SwipeMapDialog.vue';
import { GeoJSON } from 'ol/format';
import * as turf from '@turf/turf';
import { LineString, Polygon as OLPolygon, Point, MultiPoint } from 'ol/geom';
import { toLonLat } from 'ol/proj';

// --- Props & Store ---
const props = defineProps({
  projectId: {
    type: String,
    required: true,
  },
});
const store = useStore();

// --- Ссылки на DOM и OL инстансы ---
const mapContainer = ref<HTMLElement | null>(null);
const mapParent = ref<HTMLElement | null>(null);
let map: Map | null = null;
let drawInteraction: Draw | null = null;

// --- MVT Векторные слои тайлов (создаются динамически с фильтром по projectId) ---
let pointsTileSource: VectorTileSource | null = null;
let linesTileSource: VectorTileSource | null = null;
let polygonsTileSource: VectorTileSource | null = null;

let pointsTileLayer: VectorTileLayer | null = null;
let linesTileLayer: VectorTileLayer | null = null;
let polygonsTileLayer: VectorTileLayer | null = null;

// --- Style Function для временного слоя (редактирование/рисование) ---
const tempLayerStyleFunction = (feature: any) => {
  const styles = [
    new Style({
      stroke: new Stroke({
        color: '#ffcc33',
        width: 3,
      }),
      fill: new Fill({
        color: 'rgba(255, 204, 51, 0.2)',
      }),
      image: new CircleStyle({
        radius: 7,
        fill: new Fill({ color: '#ffcc33' }),
        stroke: new Stroke({ color: '#fff', width: 2 })
      }),
    }),
  ];

  if (isGeometryEditMode.value) {
    const geometry = feature.getGeometry();
    if (geometry) {
      const type = geometry.getType();
      let coords: any[] = [];
      
      // Извлекаем все вершины для отрисовки красных точек
      if (type === 'Point') {
        coords = [geometry.getCoordinates()];
      } else if (type === 'LineString' || type === 'MultiPoint') {
        coords = geometry.getCoordinates();
      } else if (type === 'Polygon') {
        // Для полигона берем все координаты всех колец
        geometry.getCoordinates().forEach((ring: any) => coords.push(...ring));
      } else if (type === 'MultiLineString') {
        geometry.getLineStrings().forEach((ls: any) => coords.push(...ls.getCoordinates()));
      } else if (type === 'MultiPolygon') {
        geometry.getPolygons().forEach((poly: any) => {
            poly.getCoordinates().forEach((ring: any) => coords.push(...ring));
        });
      }

      if (coords.length > 0) {
        styles.push(new Style({
          image: new CircleStyle({
            radius: 5,
            fill: new Fill({
              color: '#ff0000', // Красные вершины
            }),
            stroke: new Stroke({
              color: '#ffffff',
              width: 1
            })
          }),
          geometry: new MultiPoint(coords),
        }));
      }
    }
  }
  return styles;
};

// --- Временный слой для операций редактирования/рисования (GeoJSON) ---
const tempSource = new VectorSource();
const tempLayer = new VectorLayer({
  source: tempSource,
  zIndex: 150,
  style: tempLayerStyleFunction,
  properties: { 'willReadFrequently': true }
});

// --- Слой для измерений ---
const measureSource = new VectorSource();
const measureLayer = new VectorLayer({
  source: measureSource,
  zIndex: 200,
  style: new Style({
    fill: new Fill({
      color: 'rgba(255, 171, 0, 0.2)',
    }),
    stroke: new Stroke({
      color: '#ffab00',
      lineDash: [10, 10],
      width: 2,
    }),
    image: new CircleStyle({
      radius: 5,
      stroke: new Stroke({
        color: '#ffab00',
      }),
      fill: new Fill({
        color: 'rgba(255, 255, 255, 0.7)',
      }),
    }),
  }),
});

// --- Источник для снаппинга (собирает фичи из MVT в текущем вью) ---
const snapSource = new VectorSource();

// Кэш для распарсенных характеристик MVT-фич (RenderFeature не поддерживает .set())
let characteristicsCache = new WeakMap();

// --- Состояние компонента ---
const drawMode = ref<'Point' | 'MultiLineString' | 'Polygon' | null>(null);
const measureMode = ref<'length' | 'area' | null>(null);
const isBufferMode = ref(false);
const bufferDistance = ref(100);
const bufferCenterCoords = ref<number[]>([0, 0]);
const bufferSourceFeature = ref<any>(null); // Acts as a flag if center is set
let bufferDrawInteraction: Draw | null = null;
let bufferPreviewFeature: Feature | null = null;

const isGeometryEditMode = ref(false);
let modifyInteraction: Modify | null = null;
let measureDraw: Draw | null = null;
let snapInteraction: Snap | null = null;

// --- Shot Frame Editing ---
const SHOT_FRAME_MIN_ZOOM = Number(import.meta.env.VITE_SHOT_FRAME_MIN_ZOOM || 16);
const isZoomHighEnough = ref(false);
const modifiedSubIds = ref(new Set<number>());
const isLoadingParts = ref(false);
const cacheBuster = ref(Date.now());

// Тултипы для измерений
let measureTooltipElement: HTMLElement | null = null;
let measureTooltip: Overlay | null = null;
const helpTooltipElement = ref<HTMLElement | null>(null);
let helpTooltip: Overlay | null = null;
const activeMeasureTooltips = shallowRef<Overlay[]>([]);
const visibleLayerIds = ref<string[]>([]);
const selectedTerrainLayerId = ref<string | null>(null);
const activeImageLayers = shallowRef<Record<string, TileLayer<TileWMS>>>({});
const layerOpacities = ref<Record<string, number>>({});
const imageryMenuOpen = ref(false);
const autoExtentEnabled = ref(false);
const swipeMapVisible = ref(false);

// --- Состояние импорта ---
const importFileDialog = ref(false);
const importFile = ref<File | null>(null);
const isImporting = ref(false);

const selectedFeatureId = computed(() => store.state.geodata.selectedFeatureId);
const selectedFeature = computed(() => {
  if (!selectedFeatureId.value) return null;

  const point = points.value.find(f => f.id === selectedFeatureId.value);
  if (point) return { ...point, type: 'Point' };

  const multiline = multilines.value.find(f => f.id === selectedFeatureId.value);
  if (multiline) return { ...multiline, type: 'MultiLineString' };

  const polygon = polygons.value.find(f => f.id === selectedFeatureId.value);
  if (polygon) return { ...polygon, type: 'Polygon' };

  return null;
});

// --- Состояние для нового объекта ---
const metadataDialog = ref(false);
const drawingType = ref('');
const newObjectGeometry = ref<any>(null);
const pointTypes = ['camera', 'pillar', 'other'];
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
const points = computed<ProjectPoint[]>(() => store.state.geodata.points);
const multilines = computed<ProjectMultiline[]>(() => store.state.geodata.multilines);
const polygons = computed<ProjectPolygon[]>(() => store.state.geodata.polygons);

// Мгновенный фильтр видимости на основе данных из Vuex (до того, как обновятся тайлы в БД)
const hiddenFeatureIds = computed(() => {
  const hidden = new Set<string>();
  points.value.forEach(p => { if (p.characteristics?.visible === false) hidden.add(p.id); });
  multilines.value.forEach(l => { if (l.characteristics?.visible === false) hidden.add(l.id); });
  polygons.value.forEach(p => { if (p.characteristics?.visible === false) hidden.add(p.id); });
  return hidden;
});

// --- Обработка клика по MVT тайлам ---
const handleMapClick = (event: any) => {
  if (isGeometryEditMode.value || measureMode.value || isBufferMode.value) return;

  // Ограничиваем клик только нашими MVT слоями тайлов
  const feature = map?.forEachFeatureAtPixel(event.pixel, (f, layer) => {
    if (layer === pointsTileLayer || layer === linesTileLayer || layer === polygonsTileLayer) {
      return f;
    }
    return null;
  });

  if (feature && feature.get('id') !== undefined) {
    store.dispatch('geodata/selectFeature', feature.get('id'));
  } else {
    store.dispatch('geodata/selectFeature', null);
  }
};

// --- Логика буфера ---
const toggleBufferMode = () => {
  isBufferMode.value = !isBufferMode.value;
};

// Следим за режимом буфера для активации/деактивации инструментов
watch(isBufferMode, (active) => {
  if (active) {
    // Выключаем другие инструменты
    measureMode.value = null;
    drawMode.value = null;
    bufferSourceFeature.value = null;
    clearMeasurements();
    startBufferDrawing();
  } else {
    stopBufferDrawing();
    // При выключении режима буфера очищаем только временные замеры, 
    // если пользователь хочет оставить результат - он должен был зафиксировать его (уже реализовано в drawend)
    bufferSourceFeature.value = null;
    bufferCenterCoords.value = [0, 0];
  }
});

const startBufferDrawing = () => {
  if (!map) return;
  updateSnapSource();

  bufferDrawInteraction = new Draw({
    source: new VectorSource(),
    type: 'LineString',
    maxPoints: 2,
    style: new Style({
      image: new CircleStyle({
        radius: 5,
        stroke: new Stroke({ color: '#ffab00', width: 2 }),
        fill: new Fill({ color: 'rgba(255, 255, 255, 0.7)' }),
      }),
      stroke: new Stroke({ color: 'transparent' }) 
    }),
  });

  bufferDrawInteraction.on('drawstart', (evt) => {
    const sketch = evt.feature;
    const coords = (sketch.getGeometry() as LineString).getCoordinates();
    bufferCenterCoords.value = coords[0];
    
    bufferSourceFeature.value = new Feature({
      geometry: new Point(bufferCenterCoords.value)
    });

    map?.on('pointermove', handleBufferPointerMove);
  });

  bufferDrawInteraction.on('drawend', () => {
    map?.un('pointermove', handleBufferPointerMove);
    
    if (bufferPreviewFeature) {
      const finalBuffer = bufferPreviewFeature.clone();
      const finalCenter = new Feature(new Point(bufferCenterCoords.value));
      measureSource.addFeature(finalCenter);
      measureSource.addFeature(finalBuffer);
    }
    
    bufferPreviewFeature = null;
    bufferCenterCoords.value = [0, 0];
    bufferSourceFeature.value = null; 
  });

  map.addInteraction(bufferDrawInteraction);

  snapInteraction = new Snap({ source: snapSource });
  map.addInteraction(snapInteraction);
};

const stopBufferDrawing = () => {
  if (map) {
    if (bufferDrawInteraction) {
      map.removeInteraction(bufferDrawInteraction);
      bufferDrawInteraction = null;
    }
    if (snapInteraction) {
      map.removeInteraction(snapInteraction);
      snapInteraction = null;
    }
    map.un('pointermove', handleBufferPointerMove);
  }
  
  // Удаляем активное превью, если оно было
  if (bufferPreviewFeature) {
    measureSource.removeFeature(bufferPreviewFeature);
    bufferPreviewFeature = null;
  }
};

const handleBufferPointerMove = (event: any) => {
  if (!bufferSourceFeature.value || !bufferCenterCoords.value) return;

  const center = bufferCenterCoords.value;
  const current = event.coordinate;
  
  const distance = geoCalcService.calculateDistance(center, current);
  bufferDistance.value = Math.max(1, Math.round(distance));
};

const updateBufferPreview = () => {
  if (!bufferSourceFeature.value || !bufferCenterCoords.value || !isBufferMode.value) return;
  
  try {
    const point = turf.point(toLonLat(bufferCenterCoords.value));
    const buffered = turf.buffer(point, bufferDistance.value, { units: 'meters' });

    const bufferedFeatures = geoJsonFormat.readFeatures(buffered, {
      dataProjection: 'EPSG:4326',
      featureProjection: 'EPSG:3857'
    });

    // Удаляем старое превью перед добавлением нового
    if (bufferPreviewFeature) {
      measureSource.removeFeature(bufferPreviewFeature);
    }
    
    bufferPreviewFeature = bufferedFeatures[0];
    measureSource.addFeature(bufferPreviewFeature);

  } catch (err) {
    console.error("Buffer calculation failed", err);
  }
};

watch([bufferDistance, bufferSourceFeature, isBufferMode], () => {
  if (isBufferMode.value) {
    updateBufferPreview();
  }
});

const prepareSaveBuffer = () => {
  if (!bufferPreviewFeature) return;
  
  const geometry = bufferPreviewFeature.getGeometry();
  if (geometry) {
    newObjectGeometry.value = geoJsonFormat.writeGeometryObject(geometry, {
      featureProjection: 'EPSG:3857',
      dataProjection: 'EPSG:4326'
    });
    
    drawingType.value = 'Polygon';
    newObjectMetadata.value = { 
      name: `Buffer of ${bufferSourceFeature.value.get('name') || 'Object'} (${bufferDistance.value}m)`, 
      description: `Automatically generated buffer zone with radius ${bufferDistance.value} meters.`, 
      status: 'IN_PROCESS' as Status, 
      type: 'other', 
      characteristics: {} 
    };
    metadataDialog.value = true;
    isBufferMode.value = false;
  }
};

// --- Универсальная Style Function для MVT-тайлов ---
const vectorTileStyleFunction = (feature: any) => {
  const id = feature.get('id');
  
  // 1. Скрываем объект на MVT-слое, если он находится в режиме гео-редактирования
  if (isGeometryEditMode.value && id === selectedFeatureId.value) {
    return new Style();
  }

  // 2. Мгновенный фильтр видимости на основе состояния Vuex
  if (id && hiddenFeatureIds.value.has(id)) {
    return new Style();
  }

  // 3. Достаем характеристики из кэша
  let characteristics = characteristicsCache.get(feature);
  
  if (!characteristics) {
    const charProp = feature.get('characteristics');
    if (charProp) {
      if (typeof charProp === 'string' && (charProp.startsWith('{') || charProp.startsWith('['))) {
        try {
          characteristics = JSON.parse(charProp);
        } catch (e) {
          // Если не парсится, используем плоские свойства фичи
          characteristics = feature.getProperties();
        }
      } else {
        characteristics = charProp;
      }
    } else {
      characteristics = feature.getProperties();
    }
    
    // Сохраняем в WeakMap для этого инстанса фичи
    if (characteristics && typeof characteristics === 'object') {
      characteristicsCache.set(feature, characteristics);
    }
  }

  // 4. Проверка видимости из данных самого тайла (как запасной вариант)
  const isVisibleInTile = characteristics?.visible !== false && characteristics?.isVisible !== false;
  if (!isVisibleInTile) {
    return new Style();
  }

  const name = feature.get('name');
  
  // 5. Вызываем существующий парсер стилей
  return parseStyle(characteristics, name);
};

// При изменении списка скрытых объектов принудительно перерисовываем MVT слои
watch(hiddenFeatureIds, () => {
  if (pointsTileLayer) pointsTileLayer.changed();
  if (linesTileLayer) linesTileLayer.changed();
  if (polygonsTileLayer) polygonsTileLayer.changed();
}, { deep: true });

// --- Инициализация MVT источников и слоев ---
const initMvtLayers = (projectId: string) => {
  if (!map) return;

  // Удаляем старые MVT слои, если они существуют
  if (pointsTileLayer) map.removeLayer(pointsTileLayer);
  if (linesTileLayer) map.removeLayer(linesTileLayer);
  if (polygonsTileLayer) map.removeLayer(polygonsTileLayer);

  // Создаем MVT источники с использованием специализированных функций PostGIS (Function Layers)
  pointsTileSource = new VectorTileSource({
    format: new MVT(),
    url: `/tiles/geodata.mvt_project_points/{z}/{x}/{y}.pbf?project_id_param=${projectId}&t={cacheBuster}`,
    maxZoom: 20,
    tileUrlFunction: (tileCoord) => {
      return `/tiles/geodata.mvt_project_points/${tileCoord[0]}/${tileCoord[1]}/${tileCoord[2]}.pbf?project_id_param=${projectId}&t=${cacheBuster.value}`;
    }
  });

  linesTileSource = new VectorTileSource({
    format: new MVT(),
    url: `/tiles/geodata.mvt_project_multilines/{z}/{x}/{y}.pbf?project_id_param=${projectId}&t={cacheBuster}`,
    maxZoom: 20,
    tileUrlFunction: (tileCoord) => {
      return `/tiles/geodata.mvt_project_multilines/${tileCoord[0]}/${tileCoord[1]}/${tileCoord[2]}.pbf?project_id_param=${projectId}&t=${cacheBuster.value}`;
    }
  });

  polygonsTileSource = new VectorTileSource({
    format: new MVT(),
    url: `/tiles/geodata.mvt_project_polygons/{z}/{x}/{y}.pbf?project_id_param=${projectId}&t={cacheBuster}`,
    maxZoom: 20,
    tileUrlFunction: (tileCoord) => {
      return `/tiles/geodata.mvt_project_polygons/${tileCoord[0]}/${tileCoord[1]}/${tileCoord[2]}.pbf?project_id_param=${projectId}&t=${cacheBuster.value}`;
    }
  });

  // Создаем слои
  polygonsTileLayer = new VectorTileLayer({
    source: polygonsTileSource,
    style: vectorTileStyleFunction,
    zIndex: 98,
    renderMode: 'vector'
  });

  linesTileLayer = new VectorTileLayer({
    source: linesTileSource,
    style: vectorTileStyleFunction,
    zIndex: 99,
    renderMode: 'vector'
  });

  pointsTileLayer = new VectorTileLayer({
    source: pointsTileSource,
    style: vectorTileStyleFunction,
    zIndex: 100,
    renderMode: 'vector'
  });

  // Добавляем слои на карту
  map.addLayer(polygonsTileLayer);
  map.addLayer(linesTileLayer);
  map.addLayer(pointsTileLayer);
};

const refreshMvtSources = () => {
  characteristicsCache = new WeakMap();
  if (pointsTileSource) pointsTileSource.refresh();
  if (linesTileSource) linesTileSource.refresh();
  if (polygonsTileSource) polygonsTileSource.refresh();
};

// --- Обновление источника снаппинга из данных Vuex ---
const updateSnapSource = () => {
  if (!map) return;
  snapSource.clear();
  
  // Используем объекты из стора для снаппинга, так как они уже загружены в память
  const allObjects = [...points.value, ...multilines.value, ...polygons.value];
  const features: Feature[] = [];
  
  allObjects.forEach(obj => {
    if (obj.geom) {
      const read = geoJsonFormat.readFeatures(obj.geom, {
        dataProjection: 'EPSG:4326',
        featureProjection: 'EPSG:3857'
      });
      if (Array.isArray(read)) {
        features.push(...read);
      } else if (read) {
        features.push(read);
      }
    }
  });
  
  snapSource.addFeatures(features);
};

// --- Логика измерений ---
const createMeasureTooltip = () => {
  if (measureTooltipElement) {
    measureTooltipElement.parentNode?.removeChild(measureTooltipElement);
  }
  measureTooltipElement = document.createElement('div');
  measureTooltipElement.className = 'ol-tooltip ol-tooltip-measure';
  measureTooltip = new Overlay({
    element: measureTooltipElement,
    offset: [0, -15],
    positioning: 'bottom-center',
    stopEvent: false,
    insertFirst: false,
  });
  map?.addOverlay(measureTooltip);
  activeMeasureTooltips.value = [...activeMeasureTooltips.value, measureTooltip];
};

const clearMeasurements = () => {
  measureSource.clear();
  activeMeasureTooltips.value.forEach(ov => map?.removeOverlay(ov));
  activeMeasureTooltips.value = [];
};

const stopActiveTool = () => {
  measureMode.value = null;
  isBufferMode.value = false;
  drawMode.value = null;
  clearMeasurements();
};

watch(measureMode, (newMode) => {
  if (!map) return;

  if (measureDraw) map.removeInteraction(measureDraw);
  if (snapInteraction) map.removeInteraction(snapInteraction);

  if (!newMode) {
    helpTooltipElement.value?.classList.add('d-none');
    return;
  }

  updateSnapSource();

  const type = newMode === 'area' ? 'Polygon' : 'LineString';
  measureDraw = new Draw({
    source: measureSource,
    type: type,
    style: new Style({
      fill: new Fill({
        color: 'rgba(255, 255, 255, 0.2)',
      }),
      stroke: new Stroke({
        color: 'rgba(0, 0, 0, 0.5)',
        lineDash: [10, 10],
        width: 2,
      }),
      image: new CircleStyle({
        radius: 5,
        stroke: new Stroke({
          color: 'rgba(0, 0, 0, 0.7)',
        }),
        fill: new Fill({
          color: 'rgba(255, 255, 255, 0.2)',
        }),
      }),
    }),
  });
  map.addInteraction(measureDraw);

  // Снаппинг к объектам
  snapInteraction = new Snap({ source: snapSource });
  map.addInteraction(snapInteraction);

  createMeasureTooltip();

  let listener: any;
  measureDraw.on('drawstart', (evt) => {
    const sketch = evt.feature;
    let tooltipCoord: any;

    listener = sketch.getGeometry()?.on('change', (ev) => {
      const geom = ev.target;
      let output: string = '';
      if (geom instanceof OLPolygon) {
        output = geoCalcService.formatArea(geom);
        tooltipCoord = geom.getInteriorPoint().getCoordinates();
      } else if (geom instanceof LineString) {
        output = geoCalcService.formatLength(geom);
        tooltipCoord = geom.getLastCoordinate();
      }
      if (measureTooltipElement) {
        measureTooltipElement.innerHTML = output;
      }
      measureTooltip?.setPosition(tooltipCoord);
    });
  });

  measureDraw.on('drawend', () => {
    if (measureTooltipElement) {
      measureTooltipElement.className = 'ol-tooltip ol-tooltip-static';
      measureTooltip?.setOffset([0, -7]);
    }
    // unset sketch
    measureTooltipElement = null;
    measureTooltip = null;
    createMeasureTooltip();
    // remove listener
    // Note: in drawend listener is removed by OL automatically? No, but here we just stop using it.
  });
});

// --- Инициализация карты ---
onMounted(() => {
  if (!mapContainer.value || !mapParent.value) return;
  map = new Map({
    target: mapContainer.value,
    layers: [
      new TileLayer({ source: new OSM() }),
      tempLayer, // Слой для временного рисования/редактирования
      measureLayer, // Слой для измерений
    ],
    view: new View({
      center: [0, 0],
      zoom: 2,
    }),
  });

  // Fullscreen для контейнера карты
  const fullScreenControl = new FullScreen({
    source: mapParent.value,
    tipLabel: 'На весь экран'
  });

  map.addControl(fullScreenControl);
  map.on('click', handleMapClick);

  document.addEventListener('fullscreenchange', () => {
    map?.updateSize();
  });

  if (props.projectId) {
    initMvtLayers(props.projectId);
  }
});

// --- Очистка при размонтировании ---
onUnmounted(() => {
  if (map) {
    map.un('click', handleMapClick);
    map.setTarget(undefined);
    map = null;
  }
});

const geoJsonFormat = new GeoJSON();

const clearWmsLayers = () => {
    if (!map) return;
    for (const key in activeImageLayers.value) {
        const layerProxy = activeImageLayers.value[key];
        if (layerProxy) {
            map.removeLayer(toRaw(layerProxy));
        }
    }
    activeImageLayers.value = {};
    visibleLayerIds.value = [];
    layerOpacities.value = {};
};

// Наблюдаем за изменением projectId
watch(() => props.projectId, (newProjectId) => {
  if (newProjectId) {
    if (map) {
      map.set('projectId', newProjectId);
    }
    clearWmsLayers();
    store.commit('geodata/SET_SELECTED_PROJECT_ID', newProjectId);
    
    // Инициализируем MVT слои для нового проекта
    initMvtLayers(newProjectId);

    // Загружаем метаданные для списков, WMS, поиска и зумирования
    store.dispatch('geodata/fetchVectorSummaryForProject', newProjectId);
    store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 });
    store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
  }
}, { immediate: true });

// Наблюдаем за обновлением данных в Vuex для зумирования и обновления тайлов
watch([points, multilines, polygons], () => {
  zoomToExtent();
  refreshMvtSources();
  if (measureMode.value) {
    updateSnapSource();
  }
});

// При изменении выбранного объекта зумируемся к нему
watch(selectedFeatureId, (newId) => {
  if (!newId || !map || !autoExtentEnabled.value || !store.state.geodata.lastSelectionShouldZoom) return;
  
  // При работе с MVT мы не можем гарантировать наличие фичи в клиентском кэше VectorTileSource.
  // Поэтому берем геометрию напрямую из реактивного объекта selectedFeature, загруженного в Vuex!
  if (selectedFeature.value && selectedFeature.value.geom) {
    const parsedFeatures = geoJsonFormat.readFeatures(selectedFeature.value.geom, {
      dataProjection: 'EPSG:4326',
      featureProjection: 'EPSG:3857'
    });
    const firstFeature = Array.isArray(parsedFeatures) ? parsedFeatures[0] : parsedFeatures;
    if (firstFeature) {
      const geometry = firstFeature.getGeometry();
      if (geometry) {
        const extent = geometry.getExtent();
        map.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000, maxZoom: 18 });
      }
    }
  }
});

// --- Логика переключения WMS слоев ---
const setLayerOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageLayers.value[layerId];
    if (layer) {
        toRaw(layer).setOpacity(opacity / 100);
        layerOpacities.value[layerId] = opacity;
    }
};

const toggleImageryLayer = (layerInfo: ImageryLayer) => {
    if (!map) return;
    const isVisible = visibleLayerIds.value.includes(layerInfo.id);

    if (isVisible) {
        if (activeImageLayers.value[layerInfo.id]) return;

        const wmsSource = new TileWMS({
          url: layerInfo.serviceUrl,
          params: {
            'LAYERS': layerInfo.workspace + ":" + layerInfo.layerName,
            TILED: true,
          },
          serverType: 'geoserver',
          transition: 0,
        });
        const imageLayer = new TileLayer({ 
            source: wmsSource,
            opacity: (layerOpacities.value[layerInfo.id] || 100) / 100,
        });
        map.addLayer(imageLayer);
        
        activeImageLayers.value = {
            ...activeImageLayers.value,
            [layerInfo.id]: imageLayer
        };

        if (layerOpacities.value[layerInfo.id] === undefined) {
            layerOpacities.value[layerInfo.id] = 100;
        }
    } else {
        const layerToRemove = activeImageLayers.value[layerInfo.id];
        if (layerToRemove) {
            map.removeLayer(toRaw(layerToRemove));
            
            const nextLayers = { ...activeImageLayers.value };
            delete nextLayers[layerInfo.id];
            activeImageLayers.value = nextLayers;
        }
    }
};

// --- Логика рисования новых объектов ---
watch(drawMode, (newMode) => {
    if (!map) return;

    if (drawInteraction) {
        map.removeInteraction(drawInteraction);
    }

    if (newMode) {
        drawingType.value = newMode;
        drawInteraction = new Draw({
            source: tempSource, // Рисуем на временном GeoJSON источнике
            type: newMode,
        });

        drawInteraction.on('drawend', (event) => {
            const geometry = event.feature.getGeometry();
            if (geometry) {
                newObjectGeometry.value = geoJsonFormat.writeGeometryObject(geometry, {
                    featureProjection: 'EPSG:3857',
                    dataProjection: 'EPSG:4326'
                });
                
                newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status, type: 'other', characteristics: {} };
                metadataDialog.value = true;
            }
            drawMode.value = null;
        });

        map.addInteraction(drawInteraction);
    }
});

const cancelNewFeature = () => {
  metadataDialog.value = false;
  tempSource.clear();
  newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status, type: 'other', characteristics: {} };
  newObjectCameraDetails.value = { ip_address: '', port: 8000, login: '', password: '' };
};

const saveNewFeature = async () => {
    if (!newObjectGeometry.value || !props.projectId) return;

    let characteristics = { type: newObjectMetadata.value.type };
    if (newObjectMetadata.value.type === 'camera') {
      characteristics = { ...characteristics, ...newObjectCameraDetails.value };
    }

    const payload = {
        projectId: props.projectId,
        folderId: store.state.geodata.selectedFolderId,
        geom: ensureMultiType(newObjectGeometry.value),
        name: newObjectMetadata.value.name,
        description: newObjectMetadata.value.description,
        status: newObjectMetadata.value.status,
        characteristics: characteristics,
    };

    await store.dispatch('geodata/createFeature', { type: drawingType.value, data: payload });

    metadataDialog.value = false;
    cancelNewFeature();
    
    // Обновляем MVT источники, чтобы подгрузить новый объект
    refreshMvtSources();
};

// --- Логика редактирования геометрии (Shot Frame Оптимизация) ---

const fetchParts = debounce(async () => {
  if (!isGeometryEditMode.value || !isZoomHighEnough.value || !selectedFeatureId.value || !selectedFeature.value) return;

  const view = map?.getView();
  if (!view) return;
  
  const extent = view.calculateExtent(map!.getSize());
  const minXminY = toLonLat([extent[0], extent[1]]);
  const maxXmaxY = toLonLat([extent[2], extent[3]]);

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
      minXminY[0], minXminY[1], maxXmaxY[0], maxXmaxY[1]
    );
    
    const parts = response.data;
    
    const features = parts.map((part: any) => {
        const result = geoJsonFormat.readFeature(part.geojson, {
            dataProjection: 'EPSG:4326',
            featureProjection: 'EPSG:3857'
        });
        const feature = Array.isArray(result) ? result[0] : result;
        if (feature) {
            (feature as any).set('sub_id', part.subId);
            (feature as any).setId(`${selectedFeatureId.value}_${part.subId}`);
        }
        return feature;
    }).filter((f: any) => !!f);

    features.forEach((f: any) => {
        if (!tempSource.getFeatureById(f.getId() as string)) {
            tempSource.addFeature(f);
        }
    });

  } catch (err) {
    console.error("Failed to fetch geometry parts:", err);
  } finally {
    isLoadingParts.value = false;
  }
}, 1000);

const enterGeometryEditMode = () => {
    if (!map || !selectedFeatureId.value || !selectedFeature.value) return;

    const currentZoom = map.getView().getZoom() || 0;
    isZoomHighEnough.value = currentZoom >= SHOT_FRAME_MIN_ZOOM;

    if (!isZoomHighEnough.value) {
        alert(`Please zoom in to at least level ${SHOT_FRAME_MIN_ZOOM} to edit this complex object.`);
        return;
    }

    isGeometryEditMode.value = true;
    tempLayer.changed();
    modifiedSubIds.value.clear();
    tempSource.clear();

    // Принудительно инвалидируем стиль MVT слоев, чтобы скрыть дубликат на время редактирования
    if (pointsTileLayer) pointsTileLayer.setStyle(vectorTileStyleFunction);
    if (linesTileLayer) linesTileLayer.setStyle(vectorTileStyleFunction);
    if (polygonsTileLayer) polygonsTileLayer.setStyle(vectorTileStyleFunction);

    if (drawInteraction) {
      map.removeInteraction(drawInteraction);
    }
    drawMode.value = null;

    modifyInteraction = new Modify({
        source: tempSource,
    });
    
    modifyInteraction.on('modifyend', (evt) => {
        evt.features.forEach((f: any) => {
            const subId = f.get('sub_id');
            if (subId !== undefined) {
                modifiedSubIds.value.add(subId);
            }
        });
    });

    map.addInteraction(modifyInteraction);
    
    // Подписываемся на перемещение карты для подгрузки новых частей
    map.on('moveend', handleMapMoveForShotFrame);
    
    // Initial fetch
    fetchParts();
};

const handleMapMoveForShotFrame = () => {
    if (!isGeometryEditMode.value || !map) return;
    
    const currentZoom = map.getView().getZoom() || 0;
    isZoomHighEnough.value = currentZoom >= SHOT_FRAME_MIN_ZOOM;
    
    if (isZoomHighEnough.value) {
        fetchParts();
    }
};

const confirmGeometryEdit = async () => {
    if (!selectedFeature.value || !selectedFeatureId.value) return;

    const apiTypeMap: Record<string, 'points' | 'multilines' | 'polygons'> = {
        'Point': 'points',
        'MultiLineString': 'multilines',
        'Polygon': 'polygons'
    };
    const apiType = apiTypeMap[selectedFeature.value.type];

    if (modifiedSubIds.value.size > 0) {
        const partsToUpdate = Array.from(modifiedSubIds.value).map(subId => {
            const feature = tempSource.getFeatureById(`${selectedFeatureId.value}_${subId}`);
            if (!feature) return null;
            
            const geom = feature.getGeometry();
            if (!geom) return null;
            
            const geojson = geoJsonFormat.writeGeometry(geom, {
                featureProjection: 'EPSG:3857',
                dataProjection: 'EPSG:4326'
            });
            
            return { subId, geojson };
        }).filter(p => p !== null) as { subId: number, geojson: string }[];

        if (partsToUpdate.length > 0) {
            await GeodataService.updateGeometryParts(apiType, selectedFeatureId.value, partsToUpdate);
        }
    }

    exitGeometryEditMode();
    tempSource.clear();
    refreshMvtSources();
    
    // Update data in store to reflect changes in details panel
    if (props.projectId) {
        store.dispatch('geodata/fetchVectorDataForProject', props.projectId);
    }
};

const cancelGeometryEdit = () => {
    exitGeometryEditMode();
    tempSource.clear();
    if (props.projectId) {
        store.dispatch('geodata/fetchVectorDataForProject', props.projectId);
    }
};

const exitGeometryEditMode = () => {
    if (map) {
        if (modifyInteraction) {
            map.removeInteraction(modifyInteraction);
            modifyInteraction = null;
        }
        map.un('moveend', handleMapMoveForShotFrame);
    }
    isGeometryEditMode.value = false;
    tempLayer.changed();
    modifiedSubIds.value.clear();
    
    // Восстанавливаем отрисовку MVT слоев
    if (pointsTileLayer) pointsTileLayer.setStyle(vectorTileStyleFunction);
    if (linesTileLayer) linesTileLayer.setStyle(vectorTileStyleFunction);
    if (polygonsTileLayer) polygonsTileLayer.setStyle(vectorTileStyleFunction);
};

// --- Зум на полный охват проекта на основе координат из БД ---
const zoomToExtent = () => {
  if (!map) return;
  const allObjects = [...points.value, ...multilines.value, ...polygons.value];
  if (allObjects.length === 0) return;

  const extent = createEmpty();
  allObjects.forEach(obj => {
    if (obj.geom) {
      const readFeatures = geoJsonFormat.readFeatures(obj.geom, {
        dataProjection: 'EPSG:4326',
        featureProjection: 'EPSG:3857'
      });
      const featureArray = Array.isArray(readFeatures) ? readFeatures : [readFeatures];
      featureArray.forEach(f => {
        if (f) {
          const geom = f.getGeometry();
          if (geom) {
            extend(extent, geom.getExtent());
          }
        }
      });
    }
  });

  if (extent && extent.every(isFinite) && (extent[0] !== Infinity)) {
    map.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000 });
  }
};

// --- KML Импорт ---
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
    refreshMvtSources();
  } catch (error) {
    console.error("Import failed:", error);
  } finally {
    isImporting.value = false;
  }
};
</script>

<style scoped>
.map-container {
  position: relative;
  width: 100%;
  height: 100%;
}

.map {
  width: 100%;
  height: 100%;
}

.map-overlay {
  position: absolute;
  background-color: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  padding: 8px;
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

.top-center-layers {
  top: 250px;
  right: 100px;
  width: 100px;
}

.top-right-objects {
    top: 250px;
    right: 10px;
    width: 250px;
}

.top-right-details {
    top: 40px;
    right: 10px;
    width: 400px;
    max-height: calc(100% - 20px);
    overflow-y: auto;
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

.buffer-panel {
  bottom: 20px;
  right: 60px; /* Position to the left of the bottom-right tools */
  width: 200px;
  z-index: 1000;
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

/* Styles for OpenLayers Tooltips */
:deep(.ol-tooltip) {
  position: relative;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 4px;
  color: white;
  padding: 4px 8px;
  opacity: 0.7;
  white-space: nowrap;
  font-size: 12px;
  cursor: default;
  user-select: none;
}
:deep(.ol-tooltip-measure) {
  opacity: 1;
  font-weight: bold;
}
:deep(.ol-tooltip-static) {
  background-color: #ffab00;
  color: black;
  border: 1px solid white;
  opacity: 1;
}
:deep(.ol-tooltip-measure:before),
:deep(.ol-tooltip-static:before) {
  border-top: 6px solid rgba(0, 0, 0, 0.7);
  border-right: 6px solid transparent;
  border-left: 6px solid transparent;
  content: "";
  position: absolute;
  bottom: -6px;
  margin-left: -7px;
  left: 50%;
}
:deep(.ol-tooltip-static:before) {
  border-top-color: #ffab00;
}

.altitude-checkbox :deep(.v-label) {
  font-size: 0.75rem;
}
</style>
