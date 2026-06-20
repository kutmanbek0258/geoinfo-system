<template>
  <MapBaseLayout>
    <template #engine>
      <div ref="mapParent" class="map-container">
        <div id="map" ref="mapContainer" class="map"></div>
      </div>
    </template>

    <template #top-left>
      <SearchComponent />
      <v-card class="mt-2 feature-list-card" max-height="60vh">
        <GeoObjectTree />
      </v-card>

      <!-- Analysis tasks panel (under object tree) -->
      <v-card class="mt-2" elevation="2" style="background: rgba(255,255,255,0.93)">
        <AnalysisTasksPanel :set-visible="stagingControl.setVisible" />
      </v-card>
    </template>

    <template #top-right>
      <div class="d-flex flex-column align-end">
        <v-btn icon="mdi-terrain" color="white" class="mb-2" elevation="2" @click="showTerrainDialog = true" title="Загрузить рельеф">
          <v-icon color="brown">mdi-terrain</v-icon>
        </v-btn>

        <v-btn icon="mdi-satellite-variant" color="white" class="mb-2" elevation="2" @click="showSatelliteDialog = true" title="Обработка спутниковых снимков">
          <v-icon color="primary">mdi-satellite-variant</v-icon>
        </v-btn>

        <MapLayersControl
          :layers="imageryLayers"
          v-model:visibleIds="visibleLayerIds"
          :opacities="layerOpacities"
          @toggle="toggleImageryLayer"
          @update:opacity="({id, value}) => setLayerOpacity(id, value)"
        />

        <v-btn icon="mdi-magnify-scan" color="white" class="mb-2" elevation="2" @click="zoomToExtent" title="Zoom to extent">
          <v-icon color="primary">mdi-magnify-scan</v-icon>
        </v-btn>

        <v-btn icon="mdi-target-variant" :color="autoExtentEnabled ? 'primary' : 'white'" class="mb-2" elevation="2" @click="autoExtentEnabled = !autoExtentEnabled">
          <v-icon :color="autoExtentEnabled ? 'white' : 'primary'">mdi-target-variant</v-icon>
        </v-btn>

        <PrintDialog :map="map" />
      </div>

      <div v-if="selectedFeatureId && !isGeometryEditMode" class="mt-2" style="width: 400px">
        <ObjectDetails
          :feature-id="selectedFeatureId"
          :feature-name="selectedFeature?.name"
          :feature-description="selectedFeature?.description"
          :feature-type="selectedFeature?.type || ''"
          :feature-image-url="selectedFeature?.imageUrl"
          :full-feature-data="selectedFeature"
          @close="selectFeature(null)"
          @edit-geometry="enterGeometryEditMode"
        />
      </div>
    </template>

    <template #bottom-right>
      <div v-if="isGeometryEditMode" class="mb-2 d-flex">
        <v-btn icon="mdi-check" color="success" class="mr-2" @click="confirmGeometryEdit(refreshMvtSources)" title="Confirm Changes"></v-btn>
        <v-btn icon="mdi-close" color="error" @click="cancelGeometryEdit" title="Cancel Changes"></v-btn>
      </div>

      <div class="d-flex flex-column align-end">
        <MapAnalysisMenu class="mb-2" @select-tool="onSelectAnalysisTool" />
        <MapToolsMenu
          :active-tool="!!(measureMode || isBufferMode || drawMode)"
          v-model:measureMode="measureMode"
          v-model:isBufferMode="isBufferMode"
          @stop="stopActiveTool"
          @import="openImportFileDialog"
          @clear="clearMeasurements"
          @swipe="swipeMapVisible = true"
        />
      </div>

      <v-btn-toggle v-model="drawMode" variant="elevated" density="comfortable" class="mt-2">
        <v-btn value="Point" title="Add Point"><v-icon>mdi-map-marker</v-icon></v-btn>
        <v-btn value="MultiLineString" title="Add Line"><v-icon>mdi-vector-polyline</v-icon></v-btn>
        <v-btn value="Polygon" title="Add Polygon"><v-icon>mdi-vector-polygon</v-icon></v-btn>
      </v-btn-toggle>
    </template>

    <template #center>
      <v-fade-transition>
        <div v-if="isGeometryEditMode && isZoomHighEnough" class="shot-frame-overlay">
          <div class="shot-frame-label">SHOT FRAME ACTIVE</div>
        </div>
      </v-fade-transition>
    </template>

    <template #overlays>
      <ClipRasterDialog v-model:show="showClipRasterDialog" @task-created="onAnalysisTaskCreated" />
      <TerrainContoursDialog v-model:show="showContoursDialog" @task-created="onAnalysisTaskCreated" />
      <ZonalStatisticsDialog v-model:show="showZonalStatsDialog" @task-created="onAnalysisTaskCreated" />
      <MapImportDialog v-model="importFileDialog" v-model:file="importFile" :loading="isImporting" @execute="executeFileImport" />
      <MapMetadataDialog
        v-model="metadataDialog"
        :drawing-type="drawingType"
        :metadata="newObjectMetadata"
        :camera-details="newObjectCameraDetails"
        :point-types="pointTypes"
        @cancel="cancelNewFeature"
        @save="saveNewFeature(refreshMvtSources)"
      />
      <MapBufferPanel
        v-model:active="isBufferMode"
        :has-center="!!bufferSourceFeature"
        :center="bufferCenterCoords"
        v-model:distance="bufferDistance"
      />
      <SwipeMapDialog v-model="swipeMapVisible" />
      <TerrainUploadDialog v-model="showTerrainDialog" :project-id="projectId" @uploaded="store.dispatch('geodata/fetchTerrainJobs', { page: 0, size: 10 })" />
      <SatelliteImageryUploadDialog v-model="showSatelliteDialog" @uploaded="store.dispatch('geodata/fetchTerrainJobs', { page: 0, size: 10 })" />
    </template>
  </MapBaseLayout>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, toRaw, computed, shallowRef } from 'vue';
import { useStore } from 'vuex';
import 'ol/ol.css';
import { Map, View } from 'ol';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import VectorLayer from 'ol/layer/Vector';
import { Style, Stroke, Fill, Circle as CircleStyle } from 'ol/style';
import { MultiPoint } from 'ol/geom';
import { createEmpty, extend } from 'ol/extent';
import { FullScreen } from 'ol/control';
import { GeoJSON } from 'ol/format';

import MapBaseLayout from './shared/MapBaseLayout.vue';
import MapLayersControl from './controls/MapLayersControl.vue';
import MapAnalysisMenu from './controls/MapAnalysisMenu.vue';
import TerrainContoursDialog from './shared/TerrainContoursDialog.vue';
import ZonalStatisticsDialog from './shared/ZonalStatisticsDialog.vue';
import ClipRasterDialog from './shared/ClipRasterDialog.vue';
import MapToolsMenu from './controls/MapToolsMenu.vue';
import MapImportDialog from './shared/MapImportDialog.vue';
import MapMetadataDialog from './shared/MapMetadataDialog.vue';
import MapBufferPanel from './shared/MapBufferPanel.vue';
import SwipeMapDialog from './SwipeMapDialog.vue';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';
import GeoObjectTree from './GeoObjectTree.vue';
import AnalysisTasksPanel from './shared/AnalysisTasksPanel.vue';
import PrintDialog from '@/components/print/PrintDialog.vue';
import TerrainUploadDialog from '@/components/geo-abstraction/TerrainUploadDialog.vue';
import SatelliteImageryUploadDialog from '@/components/geo-abstraction/SatelliteImageryUploadDialog.vue';

import { useMapCommonState } from '@/composables/map/shared/useMapCommonState';
import { useMapMetadata } from '@/composables/map/shared/useMapMetadata';
import { useMapImport } from '@/composables/map/shared/useMapImport';
import { useOlMvt } from '@/composables/map/ol/useOlMvt';
import { useOlWms } from '@/composables/map/ol/useOlWms';
import { useOlInteractions } from '@/composables/map/ol/useOlInteractions';
import { useOlShotFrame } from '@/composables/map/ol/useOlShotFrame';
import { useStagingLayers } from '@/composables/useStagingLayers';
import { Draw } from 'ol/interaction';

const props = defineProps({ projectId: { type: String, required: true } });
const store = useStore();
const projectIdRef = computed(() => props.projectId);

// --- 1. Shared Logic ---
const {
  imageryLayers, points, multilines, polygons, currentProject, hiddenFeatureIds,
  selectedFeatureId, selectedFeature, selectFeature,
  autoExtentEnabled, isGeometryEditMode, drawMode, measureMode, isBufferMode
} = useMapCommonState(props.projectId);

const {
  metadataDialog, drawingType, newObjectGeometry, pointTypes, newObjectMetadata, newObjectCameraDetails,
  cancelNewFeature, saveNewFeature
} = useMapMetadata(props.projectId);

const {
  importFileDialog, importFile, isImporting, openImportFileDialog, executeFileImport
} = useMapImport(props.projectId);

// --- 2. OL Engine State ---
const mapContainer = ref<HTMLElement | null>(null);
const mapParent = ref<HTMLElement | null>(null);
const map = shallowRef<Map | null>(null);
const geoJsonFormat = new GeoJSON();
const swipeMapVisible = ref(false);
const showTerrainDialog = ref(false);
const showSatelliteDialog = ref(false);
const bufferDistance = ref(100);

// --- Analysis State ---
const showContoursDialog = ref(false);
const showZonalStatsDialog = ref(false);
const showClipRasterDialog = ref(false);

// Staging layer OL synchronisation — called at setup level so setVisible is available
const stagingControl = useStagingLayers(map);

function onSelectAnalysisTool(pluginName: 'terrain_contours' | 'zonal_statistics' | 'clip_raster_by_mask') {
  if (pluginName === 'terrain_contours') showContoursDialog.value = true;
  else if (pluginName === 'zonal_statistics') showZonalStatsDialog.value = true;
  else if (pluginName === 'clip_raster_by_mask') showClipRasterDialog.value = true;
}

function onAnalysisTaskCreated(task: any) {
  console.log('Analysis task created:', task);
}

const initialZoomDone = computed(() => store.state.geodata.initialZoomDone);
const lastSelectionSource = computed(() => store.state.geodata.lastSelectionSource);

const { refreshMvtSources, initMvtLayers } = useOlMvt(map, projectIdRef, selectedFeatureId, hiddenFeatureIds, isGeometryEditMode);
const { visibleLayerIds, layerOpacities, setLayerOpacity, toggleImageryLayer, clearWmsLayers } = useOlWms(map);
const {
  tempSource, measureSource, clearMeasurements, updateSnapSource,
  bufferSourceFeature, bufferCenterCoords
} = useOlInteractions(map, drawMode, measureMode, isBufferMode, bufferDistance, points, multilines, polygons);

const {
  isZoomHighEnough, enterGeometryEditMode, confirmGeometryEdit, cancelGeometryEdit
} = useOlShotFrame(map, projectIdRef, selectedFeatureId, selectedFeature, isGeometryEditMode, tempSource);

// --- 3. Style Functions for local layers ---
const tempLayerStyleFunction = (feature: any) => {
  const styles = [
    new Style({
      stroke: new Stroke({ color: '#ffcc33', width: 3 }),
      fill: new Fill({ color: 'rgba(255, 204, 51, 0.2)' }),
      image: new CircleStyle({ radius: 7, fill: new Fill({ color: '#ffcc33' }), stroke: new Stroke({ color: '#fff', width: 2 }) }),
    }),
  ];

  if (isGeometryEditMode.value) {
    const geometry = feature.getGeometry();
    if (geometry) {
      const type = geometry.getType();
      let coords: any[] = [];
      if (type === 'Point') coords = [geometry.getCoordinates()];
      else if (type === 'LineString' || type === 'MultiPoint') coords = geometry.getCoordinates();
      else if (type === 'Polygon') geometry.getCoordinates().forEach((ring: any) => coords.push(...ring));
      else if (type === 'MultiLineString') geometry.getLineStrings().forEach((ls: any) => coords.push(...ls.getCoordinates()));
      else if (type === 'MultiPolygon') geometry.getPolygons().forEach((poly: any) => poly.getCoordinates().forEach((ring: any) => coords.push(...ring)));

      if (coords.length > 0) {
        styles.push(new Style({
          image: new CircleStyle({ radius: 5, fill: new Fill({ color: '#ff0000' }), stroke: new Stroke({ color: '#ffffff', width: 1 }) }),
          geometry: new MultiPoint(coords),
        }));
      }
    }
  }
  return styles;
};

const tempLayer = new VectorLayer({ source: tempSource, zIndex: 150, style: tempLayerStyleFunction });
const measureLayer = new VectorLayer({
  source: measureSource,
  zIndex: 200,
  style: new Style({
    fill: new Fill({ color: 'rgba(255, 171, 0, 0.2)' }),
    stroke: new Stroke({ color: '#ffab00', lineDash: [10, 10], width: 2 }),
    image: new CircleStyle({ radius: 5, stroke: new Stroke({ color: '#ffab00' }), fill: new Fill({ color: 'rgba(255, 255, 255, 0.7)' }) }),
  }),
});

// --- 4. Orchestration Logic ---
const stopActiveTool = () => {
  measureMode.value = null;
  isBufferMode.value = false;
  drawMode.value = null;
  clearMeasurements();
};

const handleMapClick = (event: any) => {
  if (isGeometryEditMode.value || measureMode.value || isBufferMode.value) return;
  const feature = map.value?.forEachFeatureAtPixel(event.pixel, (f, layer) => {
    if (layer?.get('zIndex') >= 98 && layer?.get('zIndex') <= 100) return f;
    return null;
  });
  if (feature && feature.get('id') !== undefined) selectFeature({ id: feature.get('id'), source: 'map' });
  else selectFeature(null);
};

let drawInteraction: Draw | null = null;
watch(drawMode, (newMode) => {
  if (!map.value) return;
  if (drawInteraction) map.value.removeInteraction(drawInteraction);
  if (newMode) {
    drawingType.value = newMode;
    drawInteraction = new Draw({ source: tempSource, type: newMode });
    drawInteraction.on('drawend', (event) => {
      const geometry = event.feature.getGeometry();
      if (geometry) {
        newObjectGeometry.value = geoJsonFormat.writeGeometryObject(geometry, { featureProjection: 'EPSG:3857', dataProjection: 'EPSG:4326' });
        metadataDialog.value = true;
      }
      drawMode.value = null;
    });
    map.value.addInteraction(drawInteraction);
  }
});

const zoomToExtent = () => {
  if (!map.value) return;

  if (currentProject.value?.bbox && currentProject.value.bbox.type === 'Polygon'){
    const features = geoJsonFormat.readFeatures(currentProject.value.bbox, { dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3857' });
    if (features.length > 0) {
      const extent = features[0].getGeometry()!.getExtent();
      map.value.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000 });
      return;
    }
  }
};

onMounted(() => {
  if (!mapContainer.value || !mapParent.value) return;
  map.value = new Map({
    target: mapContainer.value,
    layers: [new TileLayer({ source: new OSM() }), tempLayer, measureLayer],
    view: new View({ center: [0, 0], zoom: 2 }),
  });
  map.value.addControl(new FullScreen({ source: mapParent.value, tipLabel: 'На весь экран' }));
  map.value.on('click', handleMapClick);
  document.addEventListener('fullscreenchange', () => map.value?.updateSize());
  if (props.projectId) {
    initMvtLayers(props.projectId);
    setTimeout(() => { if (!initialZoomDone.value) { zoomToExtent(); store.commit('geodata/SET_INITIAL_ZOOM_DONE', true); } }, 1000);
  }
});

onUnmounted(() => {
  if (map.value) {
    map.value.un('click', handleMapClick);
    map.value.setTarget(undefined);
    map.value = null;
  }
});

watch(() => props.projectId, (newId) => {
  if (newId) {
    if (map.value) map.value.set('projectId', newId);
    clearWmsLayers();
    store.commit('geodata/SET_SELECTED_PROJECT_ID', newId);
    store.dispatch('geodata/fetchProject', newId);
    store.dispatch('geodata/fetchVectorSummaryForProject', newId);
    store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 });
    store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
    store.dispatch('geodata/fetchFolders', newId);
    store.dispatch('geodata/fetchAnalysisTasksByProject', newId);
  }
}, { immediate: true });

watch([points, multilines, polygons], () => {
  if ((points.value.length || multilines.value.length || polygons.value.length) && map.value && !initialZoomDone.value) {
    zoomToExtent();
    store.commit('geodata/SET_INITIAL_ZOOM_DONE', true);
  }
  refreshMvtSources();
  if (measureMode.value) updateSnapSource();
});
</script>

<style scoped>
.map-container, .map { width: 100%; height: 100%; }
.feature-list-card { overflow-y: auto; background-color: rgba(255, 255, 255, 0.9); }
.shot-frame-overlay {
  position: absolute; top: 15px; left: 15px; right: 15px; bottom: 15px;
  border: 2px dashed #ff5252; background-color: rgba(255, 82, 82, 0.05);
  pointer-events: none; z-index: 50; display: flex; align-items: center; justify-content: center;
}
.shot-frame-label {
  background-color: rgba(255, 82, 82, 0.8); color: white; padding: 4px 12px;
  border-radius: 4px; font-weight: bold; font-size: 12px; letter-spacing: 1px;
}
:deep(.ol-tooltip) {
  position: relative; background: rgba(0, 0, 0, 0.7); border-radius: 4px;
  color: white; padding: 4px 8px; opacity: 0.7; white-space: nowrap; font-size: 12px;
}
:deep(.ol-tooltip-static) { background-color: #ffab00; color: black; border: 1px solid white; opacity: 1; }
</style>
