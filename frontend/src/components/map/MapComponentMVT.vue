<template>
  <MapBaseLayout>
    <template #engine>
      <div ref="mapParent" class="map-container">
        <div id="map" ref="mapContainer" class="map" :style="{ cursor: isRasterValueMode ? 'crosshair' : 'default' }"></div>
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
        <v-btn icon="mdi-database-import" color="white" class="mb-2" elevation="2" @click="showProjectJobsDialog = true" title="Импортировать растровый слой">
          <v-icon color="primary">mdi-database-import</v-icon>
        </v-btn>

        <MapLayersControl
          :layers="globalRasters"
          v-model:visibleIds="visibleGlobalRasterIds"
          :opacities="globalRasterOpacities"
          @toggle="toggleImageryLayer"
          @update:opacity="({id, value}) => setGlobalRasterOpacity(id, value)"
          @update:style="handleGlobalLayerStyleChange"
          @update:colormapId="handleGlobalLayerColormapChange"
          @update:resampling="handleGlobalLayerResamplingChange"
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
        <v-btn
            icon="mdi-information-outline"
            color="white"
            class="mb-2"
            @click="showProjectProperties = true"
            title="Свойства проекта"
        ></v-btn>
        <MapAnalysisMenu class="mb-2" @select-tool="onSelectAnalysisTool" />
        <MapToolsMenu
          :active-tool="!!(measureMode || isBufferMode || drawMode || isRasterValueMode)"
          v-model:measureMode="measureMode"
          v-model:isBufferMode="isBufferMode"
          v-model:isRasterValueMode="isRasterValueMode"
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
      <SlopeDialog v-model:show="showSlopeDialog" @task-created="onAnalysisTaskCreated" />
      <AspectDialog v-model:show="showAspectDialog" @task-created="onAnalysisTaskCreated" />
      <HillshadeDialog v-model:show="showHillshadeDialog" @task-created="onAnalysisTaskCreated" />
      <ViewshedDialog v-model:show="showViewshedDialog" @task-created="onAnalysisTaskCreated" />
      <SpectralIndicesDialog v-model:show="showSpectralIndicesDialog" @task-created="onAnalysisTaskCreated" />
      <UnsupervisedClassDialog v-model:show="showUnsupervisedClassDialog" @task-created="onAnalysisTaskCreated" />
      <WatershedDelineationDialog v-model:show="showWatershedDelineationDialog" @task-created="onAnalysisTaskCreated" />
      <PolygonizeRasterDialog v-model:show="showPolygonizeRasterDialog" @task-created="onAnalysisTaskCreated" />
      <ImportDxfDialog v-model:show="showImportDxfDialog" @task-created="onAnalysisTaskCreated" />
      <RasterizeVectorDialog v-model:show="showRasterizeVectorDialog" @task-created="onAnalysisTaskCreated" />
      <RasterAlgebraDialog v-model:show="showRasterAlgebraDialog" @task-created="onAnalysisTaskCreated" />
      <RasterMosaicDialog v-model:show="showRasterMosaicDialog" @task-created="onAnalysisTaskCreated" />
      <RasterReclassDialog v-model:show="showRasterReclassDialog" @task-created="onAnalysisTaskCreated" />
      <DynamicAnalysisDialog v-model:show="showDynamicDialog" :plugin-name="activeDynamicPlugin" @task-created="onAnalysisTaskCreated" />
      <MapImportDialog v-model="importFileDialog" v-model:file="importFile" :loading="isImporting" @execute="executeFileImport" />
      <ProjectPropertiesDialog v-model="showProjectProperties" :project="currentProject" />
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
      <v-dialog v-model="showProjectJobsDialog" max-width="1200px" persistent>
        <ProjectProcessJobsManager @close="showProjectJobsDialog = false" />
      </v-dialog>

      <!-- Dialog for choosing the raster layer -->
      <v-dialog v-model="rasterValueSelectionDialog" max-width="500px" persistent>
        <v-card>
          <v-card-title class="text-h6">Выбор растрового слоя</v-card-title>
          <v-card-text>
            <div class="mb-4 text-body-2 text-medium-emphasis">
              На карте активно несколько растровых слоев. Выберите слой для запроса значения:
            </div>
            <v-radio-group v-model="selectedRasterLayer">
              <v-radio
                v-for="layer in activeRasterLayers"
                :key="layer.id"
                :label="layer.label"
                :value="layer"
              ></v-radio>
            </v-radio-group>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn variant="text" color="error" @click="cancelRasterSelection">Отмена</v-btn>
            <v-btn variant="elevated" color="primary" :disabled="!selectedRasterLayer" @click="confirmRasterSelection">Выбрать</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>

      <!-- Dialog for showing the raster value result -->
      <v-dialog v-model="rasterValueResultDialog" max-width="450px" persistent>
        <v-card>
          <v-card-title class="text-h6 d-flex align-center">
            <v-icon start color="primary" class="mr-2">mdi-eyedropper</v-icon>
            Значение растра
          </v-card-title>
          
          <v-card-text>
            <div v-if="rasterValueLoading" class="d-flex flex-column align-center py-6">
              <v-progress-circular indeterminate color="primary" size="48" class="mb-4"></v-progress-circular>
              <div class="text-body-2 text-medium-emphasis">Запрос значения растра...</div>
            </div>
            
            <div v-else-if="rasterQueryError" class="py-4">
              <v-alert type="error" variant="tonal" class="mb-4">
                {{ rasterQueryError }}
              </v-alert>
            </div>
            
            <div v-else-if="rasterQueryResult" class="py-2">
              <v-list density="compact" class="bg-transparent">
                <v-list-item>
                  <v-list-item-title class="text-caption text-medium-emphasis">Слой</v-list-item-title>
                  <v-list-item-subtitle class="text-body-1 font-weight-bold text-high-emphasis">
                    {{ selectedRasterLayer?.label }}
                  </v-list-item-subtitle>
                </v-list-item>
                
                <v-list-item>
                  <v-list-item-title class="text-caption text-medium-emphasis">Координаты</v-list-item-title>
                  <v-list-item-subtitle class="text-body-1 text-high-emphasis">
                    {{ queriedPoint?.lat.toFixed(6) }}, {{ queriedPoint?.lon.toFixed(6) }}
                  </v-list-item-subtitle>
                </v-list-item>
                
                <v-list-item>
                  <v-list-item-title class="text-caption text-medium-emphasis">Значение</v-list-item-title>
                  <v-list-item-subtitle class="text-h5 font-weight-bold text-primary">
                    {{ getDisplayValue(rasterQueryResult.values) }}
                  </v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </div>
          </v-card-text>
          
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn variant="text" @click="rasterValueResultDialog = false">Закрыть</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>

      <!-- Snackbar for Raster Value notices -->
      <v-snackbar v-model="rasterSnackbar" color="info" timeout="3000" location="bottom right">
        {{ rasterSnackbarText }}
        <template v-slot:actions>
          <v-btn variant="text" @click="rasterSnackbar = false">Закрыть</v-btn>
        </template>
      </v-snackbar>
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
import SlopeDialog from './shared/SlopeDialog.vue';
import AspectDialog from './shared/AspectDialog.vue';
import HillshadeDialog from './shared/HillshadeDialog.vue';
import ViewshedDialog from './shared/ViewshedDialog.vue';
import SpectralIndicesDialog from './shared/SpectralIndicesDialog.vue';
import UnsupervisedClassDialog from './shared/UnsupervisedClassDialog.vue';
import WatershedDelineationDialog from './shared/WatershedDelineationDialog.vue';
import PolygonizeRasterDialog from './shared/PolygonizeRasterDialog.vue';
import ImportDxfDialog from './shared/ImportDxfDialog.vue';
import RasterizeVectorDialog from './shared/RasterizeVectorDialog.vue';
import RasterAlgebraDialog from './shared/RasterAlgebraDialog.vue';
import RasterMosaicDialog from './shared/RasterMosaicDialog.vue';
import RasterReclassDialog from './shared/RasterReclassDialog.vue';
import DynamicAnalysisDialog from './shared/DynamicAnalysisDialog.vue';
import { toLonLat } from 'ol/proj';
import RasterStyleService from '@/services/raster-style.service';
import MapToolsMenu from './controls/MapToolsMenu.vue';
import MapImportDialog from './shared/MapImportDialog.vue';
import MapMetadataDialog from './shared/MapMetadataDialog.vue';
import MapBufferPanel from './shared/MapBufferPanel.vue';
import SwipeMapDialog from './SwipeMapDialog.vue';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';
import GeoObjectTree from './GeoObjectTree.vue';
import ProjectPropertiesDialog from '../projects/ProjectPropertiesDialog.vue';
import AnalysisTasksPanel from './shared/AnalysisTasksPanel.vue';
import PrintDialog from '@/components/print/PrintDialog.vue';
import ProjectProcessJobsManager from './ProjectProcessJobsManager.vue';

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
const showProjectProperties = ref(false);
const swipeMapVisible = ref(false);
const showProjectJobsDialog = ref(false);
const bufferDistance = ref(100);

// --- Analysis State ---
const showContoursDialog = ref(false);
const showZonalStatsDialog = ref(false);
const showClipRasterDialog = ref(false);
const showSlopeDialog = ref(false);
const showAspectDialog = ref(false);
const showHillshadeDialog = ref(false);
const showViewshedDialog = ref(false);
const showSpectralIndicesDialog = ref(false);
const showUnsupervisedClassDialog = ref(false);
const showWatershedDelineationDialog = ref(false);
const showPolygonizeRasterDialog = ref(false);
const showRasterizeVectorDialog = ref(false);
const showRasterAlgebraDialog = ref(false);
const showRasterMosaicDialog = ref(false);
const showRasterReclassDialog = ref(false);
const showImportDxfDialog = ref(false);
const showDynamicDialog = ref(false);
const activeDynamicPlugin = ref('');

// Staging layer OL synchronisation — called at setup level so setVisible is available
const stagingControl = useStagingLayers(map);

function onSelectAnalysisTool(pluginName: string) {
  const dynamicExists = store.state.geodata.pluginSchemas.some((s: any) => s.pluginName === pluginName);
  if (dynamicExists) {
    activeDynamicPlugin.value = pluginName;
    showDynamicDialog.value = true;
    return;
  }

  if (pluginName === 'terrain_contours') showContoursDialog.value = true;
  else if (pluginName === 'zonal_statistics') showZonalStatsDialog.value = true;
  else if (pluginName === 'clip_raster_by_mask') showClipRasterDialog.value = true;
  else if (pluginName === 'slope') showSlopeDialog.value = true;
  else if (pluginName === 'aspect') showAspectDialog.value = true;
  else if (pluginName === 'hillshade') showHillshadeDialog.value = true;
  else if (pluginName === 'viewshed_analysis') showViewshedDialog.value = true;
  else if (pluginName === 'spectral_indices') showSpectralIndicesDialog.value = true;
  else if (pluginName === 'unsupervised_class') showUnsupervisedClassDialog.value = true;
  else if (pluginName === 'watershed_delineation') showWatershedDelineationDialog.value = true;
  else if (pluginName === 'polygonize_raster') showPolygonizeRasterDialog.value = true;
  else if (pluginName === 'rasterize_vector') showRasterizeVectorDialog.value = true;
  else if (pluginName === 'raster_algebra') showRasterAlgebraDialog.value = true;
  else if (pluginName === 'raster_mosaic') showRasterMosaicDialog.value = true;
  else if (pluginName === 'raster_reclass') showRasterReclassDialog.value = true;
  else if (pluginName === 'import_dxf') showImportDxfDialog.value = true;
}

function onAnalysisTaskCreated(task: any) {
  console.log('Analysis task created:', task);
}

const initialZoomDone = computed(() => store.state.geodata.initialZoomDone);
const lastSelectionSource = computed(() => store.state.geodata.lastSelectionSource);

const { refreshMvtSources, initMvtLayers } = useOlMvt(map, projectIdRef, selectedFeatureId, hiddenFeatureIds, isGeometryEditMode);
const { visibleLayerIds, visibleGlobalRasterIds, layerOpacities, globalRasterOpacities, setLayerOpacity, setGlobalRasterOpacity, toggleImageryLayer, clearWmsLayers } = useOlWms(map);

const globalRasters = computed(() => store.state.geodata.globalRasters || []);

const handleLayerStyleChange = async ({ layerId, styleId }: { layerId: string; styleId: string | null }) => {
  let styleObj = null;
  if (styleId) {
    try {
      const response = await RasterStyleService.getRasterStyles(0, 100);
      const found = response.data.content.find((s: any) => s.id === styleId);
      if (found) styleObj = found;
    } catch (err) {
      console.error('Failed to fetch style detail for layer update:', err);
      styleObj = { id: styleId };
    }
  }
  store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId, style: styleObj });
  
  const layerInfo = imageryLayers.value.find((l: any) => l.id === layerId);
  if (layerInfo) {
    toggleImageryLayer(layerInfo, true);
  }
};

const handleLayerColormapChange = ({ layerId, colormapId }: { layerId: string; colormapId: string | null }) => {
  store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId, colormapId });
  
  const layerInfo = imageryLayers.value.find((l: any) => l.id === layerId);
  if (layerInfo) {
    toggleImageryLayer(layerInfo, true);
  }
};

const handleLayerResamplingChange = ({ layerId, resampling }: { layerId: string; resampling: string }) => {
  store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId, resampling });
  
  const layerInfo = imageryLayers.value.find((l: any) => l.id === layerId);
  if (layerInfo) {
    toggleImageryLayer(layerInfo, true);
  }
};

const handleGlobalLayerStyleChange = async ({ layerId, styleId }: { layerId: string; styleId: string | null }) => {
  let styleObj = null;
  if (styleId) {
    try {
      const response = await RasterStyleService.getRasterStyles(0, 100);
      const found = response.data.content.find((s: any) => s.id === styleId);
      if (found) styleObj = found;
    } catch (err) {
      console.error('Failed to fetch style detail for global layer update:', err);
      styleObj = { id: styleId };
    }
  }
  store.commit('geodata/UPDATE_GLOBAL_RASTER_STYLE', { layerId, style: styleObj });
  
  const layerInfo = globalRasters.value.find((l: any) => l.id === layerId);
  if (layerInfo) {
    toggleImageryLayer(layerInfo, true);
  }
};

const handleGlobalLayerColormapChange = ({ layerId, colormapId }: { layerId: string; colormapId: string | null }) => {
  store.commit('geodata/UPDATE_GLOBAL_RASTER_STYLE', { layerId, colormapId });
  
  const layerInfo = globalRasters.value.find((l: any) => l.id === layerId);
  if (layerInfo) {
    toggleImageryLayer(layerInfo, true);
  }
};

const handleGlobalLayerResamplingChange = ({ layerId, resampling }: { layerId: string; resampling: string }) => {
  store.commit('geodata/UPDATE_GLOBAL_RASTER_STYLE', { layerId, resampling });
  
  const layerInfo = globalRasters.value.find((l: any) => l.id === layerId);
  if (layerInfo) {
    toggleImageryLayer(layerInfo, true);
  }
};
const {
  tempSource, measureSource, clearMeasurements, updateSnapSource,
  bufferSourceFeature, bufferCenterCoords
} = useOlInteractions(map, drawMode, measureMode, isBufferMode, bufferDistance, points, multilines, polygons);

const {
  isZoomHighEnough, enterGeometryEditMode, confirmGeometryEdit, cancelGeometryEdit
} = useOlShotFrame(map, projectIdRef, selectedFeatureId, selectedFeature, isGeometryEditMode, tempSource);

// --- Raster Value Tool Setup ---
const isRasterValueMode = ref(false);
const rasterValueSelectionDialog = ref(false);
const rasterValueResultDialog = ref(false);
const rasterValueLoading = ref(false);
const rasterSnackbar = ref(false);
const rasterSnackbarText = ref('');
const selectedRasterLayer = ref<{ id: string; label: string; s3Url: string; layerType: 'imagery' | 'staging' } | null>(null);
const queriedPoint = ref<{ lon: number; lat: number } | null>(null);
const rasterQueryResult = ref<{ coordinates: number[]; values: number[]; band_names?: string[] } | null>(null);
const rasterQueryError = ref<string | null>(null);

const activeRasterLayers = computed(() => {
  const projectLayers = (imageryLayers.value || []).filter((l: any) => 
    visibleLayerIds.value.includes(l.id) && l.cogObjectKey
  ).map((l: any) => ({
    id: l.id,
    label: l.name,
    s3Url: `s3://geo-abstraction-input/${l.cogObjectKey}`,
    layerType: 'imagery' as const
  }));

  const stagingRasters = (store.state.geodata.stagingLayers || []).filter((l: any) => 
    l.type === 'RASTER' && 
    l.s3Url &&
    (stagingControl.visibleStagingLayerIds.value[l.taskId] !== false)
  ).map((l: any) => ({
    id: l.taskId,
    label: l.label,
    s3Url: l.s3Url,
    layerType: 'staging' as const
  }));

  return [...projectLayers, ...stagingRasters];
});

watch([measureMode, isBufferMode, drawMode], ([m, b, d]) => {
  if (m || b || d) {
    isRasterValueMode.value = false;
  }
});

watch(isRasterValueMode, (newVal) => {
  if (newVal) {
    measureMode.value = null;
    isBufferMode.value = false;
    drawMode.value = null;
    
    const layers = activeRasterLayers.value;
    if (layers.length === 0) {
      rasterSnackbarText.value = 'Нет активных растровых слоев на карте.';
      rasterSnackbar.value = true;
      isRasterValueMode.value = false;
      return;
    }
    if (layers.length === 1) {
      selectedRasterLayer.value = layers[0];
      rasterSnackbarText.value = `Выбран слой: ${layers[0].label}. Кликните на карту.`;
      rasterSnackbar.value = true;
    } else {
      rasterValueSelectionDialog.value = true;
    }
  } else {
    selectedRasterLayer.value = null;
  }
});

const cancelRasterSelection = () => {
  rasterValueSelectionDialog.value = false;
  isRasterValueMode.value = false;
  selectedRasterLayer.value = null;
};

const confirmRasterSelection = () => {
  rasterValueSelectionDialog.value = false;
  if (selectedRasterLayer.value) {
    rasterSnackbarText.value = `Выбран слой: ${selectedRasterLayer.value.label}. Кликните на карту.`;
    rasterSnackbar.value = true;
  }
};

const queryRasterValue = async (lon: number, lat: number) => {
  if (!selectedRasterLayer.value) return;
  rasterValueLoading.value = true;
  rasterQueryError.value = null;
  rasterQueryResult.value = null;
  queriedPoint.value = { lon, lat };
  rasterValueResultDialog.value = true;

  try {
    const res = await RasterStyleService.getRasterValueAtPoint(selectedRasterLayer.value.s3Url, lon, lat);
    rasterQueryResult.value = res;
  } catch (err: any) {
    console.error('Failed to query raster value:', err);
    rasterQueryError.value = err.response?.data?.detail || err.message || 'Ошибка при запросе значения растра';
  } finally {
    rasterValueLoading.value = false;
  }
};

const getDisplayValue = (values: any[]) => {
  if (!values || values.length === 0 || values[0] === null || values[0] === undefined) {
    return 'Без данных (NoData)';
  }
  return values.map(v => typeof v === 'number' ? v.toFixed(4).replace(/\.?0+$/, '') : v).join(', ');
};

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
  isRasterValueMode.value = false;
  selectedRasterLayer.value = null;
  clearMeasurements();
};

const handleMapClick = (event: any) => {
  if (isGeometryEditMode.value || measureMode.value || isBufferMode.value) return;

  if (isRasterValueMode.value && selectedRasterLayer.value) {
    const coords = event.coordinate;
    const lonLat = toLonLat(coords);
    queryRasterValue(lonLat[0], lonLat[1]);
    return;
  }

  // Перехват клика для выбора точки на карте (для Viewshed и др.)
  if (store.state.geodata.pointSelectionActive) {
    const coords = event.coordinate;
    const lonLat = toLonLat(coords);
    store.commit('geodata/SET_SELECTED_POINT', { x: lonLat[0], y: lonLat[1] });
    store.commit('geodata/SET_POINT_SELECTION_ACTIVE', false);
    return;
  }

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
    store.dispatch('geodata/fetchProjectRasters', { page: 0, size: 100 });
    store.dispatch('geodata/fetchGlobalRasters');
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
