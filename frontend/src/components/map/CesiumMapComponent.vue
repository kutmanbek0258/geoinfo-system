<template>
  <MapBaseLayout>
    <template #engine>
      <div ref="cesiumParent" class="cesium-container">
        <div ref="cesiumContainer" class="cesium-viewer"></div>
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

        <CesiumTerrainControl v-model="selectedTerrainId" :layers="terrainLayers" />

        <v-btn icon="mdi-magnify-scan" color="white" class="mb-2" elevation="2" @click="zoomToExtent" title="Zoom to extent">
          <v-icon color="primary">mdi-magnify-scan</v-icon>
        </v-btn>

        <v-btn icon="mdi-target-variant" :color="autoExtentEnabled ? 'primary' : 'white'" class="mb-2" elevation="2" @click="autoExtentEnabled = !autoExtentEnabled">
          <v-icon :color="autoExtentEnabled ? 'white' : 'primary'">mdi-target-variant</v-icon>
        </v-btn>
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
          @edit-geometry="() => enterGeometryEditMode(refreshMvtSources)"
        />
      </div>
    </template>

    <template #bottom-right>
      <div v-if="isGeometryEditMode" class="mb-2 d-flex">
        <v-btn icon="mdi-check" color="success" class="mr-2" @click="confirmGeometryEdit(refreshMvtSources)" title="Confirm Changes"></v-btn>
        <v-btn icon="mdi-close" color="error" @click="exitEditMode(refreshMvtSources)" title="Cancel Changes"></v-btn>
      </div>

      <MapAnalysisMenu class="mb-2" @select-tool="onSelectAnalysisTool" />
      <MapToolsMenu
        :active-tool="!!(measureMode || isBufferMode || drawMode)"
        v-model:measureMode="measureMode"
        v-model:isBufferMode="isBufferMode"
        @stop="stopActiveTool"
        @import="openImportFileDialog"
        @clear="clearMeasurements"
        @swipe="swipeActive = true"
      />

      <v-btn-toggle v-model="drawMode" variant="elevated" density="comfortable" class="mt-2">
        <v-btn value="Point" title="Add Point"><v-icon>mdi-map-marker</v-icon></v-btn>
        <v-btn value="MultiLineString" title="Add Line"><v-icon>mdi-vector-polyline</v-icon></v-btn>
        <v-btn value="Polygon" title="Add Polygon"><v-icon>mdi-vector-polygon</v-icon></v-btn>
      </v-btn-toggle>
    </template>

    <template #center>
      <v-fade-transition>
        <div v-if="isGeometryEditMode && isZoomHighEnough" class="shot-frame-overlay">
          <div class="shot-frame-label">SHOT FRAME ACTIVE (3D)</div>
        </div>
      </v-fade-transition>
    </template>

    <template #overlays>
      <div v-for="(tooltip, idx) in measureTooltips" :key="idx" class="cesium-tooltip" :style="getTooltipStyle(tooltip.position)">
        {{ tooltip.text }}
      </div>
      <MapImportDialog v-model="importFileDialog" v-model:file="importFile" :loading="isImporting" @execute="executeFileImport" />
      <MapMetadataDialog
        v-model="metadataDialog"
        :drawing-type="drawingType"
        :metadata="newObjectMetadata"
        :camera-details="newObjectCameraDetails"
        :point-types="['camera', 'pillar', 'other']"
        @cancel="cancelNewFeature"
        @save="saveNewFeature(refreshMvtSources)"
      />
      <MapBufferPanel
        v-model:active="isBufferMode"
        :is-3d="true"
        :has-center="!!bufferSourceEntity"
        :center="bufferCenterCoords"
        v-model:distance="bufferDistance"
      />
      <CesiumSwipeDialog v-model="swipeActive" />
      <ClipRasterDialog v-model:show="showClipRasterDialog" @task-created="onAnalysisTaskCreated" />
      <TerrainContoursDialog v-model:show="showContoursDialog" @task-created="onAnalysisTaskCreated" />
      <ZonalStatisticsDialog v-model:show="showZonalStatsDialog" @task-created="onAnalysisTaskCreated" />
      <SlopeDialog v-model:show="showSlopeDialog" @task-created="onAnalysisTaskCreated" />
      <AspectDialog v-model:show="showAspectDialog" @task-created="onAnalysisTaskCreated" />
      <HillshadeDialog v-model:show="showHillshadeDialog" @task-created="onAnalysisTaskCreated" />
      <ViewshedDialog v-model:show="showViewshedDialog" @task-created="onAnalysisTaskCreated" />
      <TerrainUploadDialog v-model="showTerrainDialog" :project-id="projectId" @uploaded="store.dispatch('geodata/fetchTerrainJobs', { page: 0, size: 10 })" />
      <SatelliteImageryUploadDialog v-model="showSatelliteDialog" @uploaded="store.dispatch('geodata/fetchTerrainJobs', { page: 0, size: 10 })" />
    </template>
  </MapBaseLayout>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, computed, toRaw, shallowRef } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
import * as turf from '@turf/turf';

import MapBaseLayout from './shared/MapBaseLayout.vue';
import MapLayersControl from './controls/MapLayersControl.vue';
import CesiumTerrainControl from './controls/CesiumTerrainControl.vue';
import MapAnalysisMenu from './controls/MapAnalysisMenu.vue';
import MapToolsMenu from './controls/MapToolsMenu.vue';
import MapImportDialog from './shared/MapImportDialog.vue';
import MapMetadataDialog from './shared/MapMetadataDialog.vue';
import MapBufferPanel from './shared/MapBufferPanel.vue';
import CesiumSwipeDialog from './CesiumSwipeDialog.vue';
import ClipRasterDialog from './shared/ClipRasterDialog.vue';
import TerrainContoursDialog from './shared/TerrainContoursDialog.vue';
import ZonalStatisticsDialog from './shared/ZonalStatisticsDialog.vue';
import SlopeDialog from './shared/SlopeDialog.vue';
import AspectDialog from './shared/AspectDialog.vue';
import HillshadeDialog from './shared/HillshadeDialog.vue';
import ViewshedDialog from './shared/ViewshedDialog.vue';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';
import GeoObjectTree from './GeoObjectTree.vue';
import TerrainUploadDialog from '@/components/geo-abstraction/TerrainUploadDialog.vue';
import SatelliteImageryUploadDialog from '@/components/geo-abstraction/SatelliteImageryUploadDialog.vue';

import { useMapCommonState } from '@/composables/map/shared/useMapCommonState';
import { useMapMetadata } from '@/composables/map/shared/useMapMetadata';
import { useMapImport } from '@/composables/map/shared/useMapImport';
import { useCesiumMvt } from '@/composables/map/cesium/useCesiumMvt';
import { useCesiumImagery } from '@/composables/map/cesium/useCesiumImagery';
import { useCesiumInteractions } from '@/composables/map/cesium/useCesiumInteractions';
import { useCesiumShotFrame } from '@/composables/map/cesium/useCesiumShotFrame';
import AnalysisTasksPanel from './shared/AnalysisTasksPanel.vue';
import { useCesiumStagingLayers } from '@/composables/map/cesium/useCesiumStagingLayers';

const props = defineProps<{ projectId: string }>();
const store = useStore();
const projectIdRef = computed(() => props.projectId);

const showContoursDialog = ref(false);
const showZonalStatsDialog = ref(false);
const showClipRasterDialog = ref(false);
const showSlopeDialog = ref(false);
const showAspectDialog = ref(false);
const showHillshadeDialog = ref(false);
const showViewshedDialog = ref(false);

function onSelectAnalysisTool(pluginName: string) {
  if (pluginName === 'terrain_contours') showContoursDialog.value = true;
  else if (pluginName === 'zonal_statistics') showZonalStatsDialog.value = true;
  else if (pluginName === 'clip_raster_by_mask') showClipRasterDialog.value = true;
  else if (pluginName === 'slope') showSlopeDialog.value = true;
  else if (pluginName === 'aspect') showAspectDialog.value = true;
  else if (pluginName === 'hillshade') showHillshadeDialog.value = true;
  else if (pluginName === 'viewshed_analysis') showViewshedDialog.value = true;
}

function onAnalysisTaskCreated(task: any) {
  console.log('Analysis task created:', task);
  // Optional: show snackbar
}

// --- 1. Shared Logic ---
const {
  imageryLayers, terrainLayers, currentProject, points, multilines, polygons, hiddenFeatureIds,
  selectedFeatureId, selectedFeature, selectFeature,
  autoExtentEnabled, isGeometryEditMode, drawMode, measureMode, isBufferMode
} = useMapCommonState(props.projectId);

const {
  metadataDialog, drawingType, newObjectGeometry, newObjectMetadata, newObjectCameraDetails,
  cancelNewFeature, saveNewFeature
} = useMapMetadata(props.projectId);

const {
  importFileDialog, importFile, isImporting, openImportFileDialog, executeFileImport
} = useMapImport(props.projectId);

// --- 2. Cesium Engine State ---
const cesiumContainer = ref<HTMLElement | null>(null);
const cesiumParent = ref<HTMLElement | null>(null);
const viewer = shallowRef<Cesium.Viewer | null>(null);
const swipeActive = ref(false);
const showTerrainDialog = ref(false);
const showSatelliteDialog = ref(false);
const bufferDistance = ref(100);

const initialZoomDone = computed(() => store.state.geodata.initialZoomDone);
const lastSelectionSource = computed(() => store.state.geodata.lastSelectionSource);

const { refreshMvtSources, initMvtLayers, raiseMvtLayersToTop } = useCesiumMvt(viewer, projectIdRef, selectedFeatureId, hiddenFeatureIds, isGeometryEditMode);
const { visibleLayerIds, layerOpacities, selectedTerrainId, setLayerOpacity, toggleImageryLayer, clearImageryLayers } = useCesiumImagery(viewer, terrainLayers, raiseMvtLayersToTop);
const stagingControl = useCesiumStagingLayers(viewer);

const sampleHeights = async (cartesianPoints: Cesium.Cartesian3[]) => {
  const v = viewer.value;
  if (!v) return cartesianPoints.map(p => {
    const c = Cesium.Cartographic.fromCartesian(p);
    return [Cesium.Math.toDegrees(c.longitude), Cesium.Math.toDegrees(c.latitude), c.height];
  });
  const cartographics = cartesianPoints.map(p => Cesium.Cartographic.fromCartesian(p));
  if (v.terrainProvider && !(v.terrainProvider instanceof Cesium.EllipsoidTerrainProvider)) {
    try {
      const sampled = await Cesium.sampleTerrainMostDetailed(v.terrainProvider, cartographics);
      return sampled.map(c => [Cesium.Math.toDegrees(c.longitude), Cesium.Math.toDegrees(c.latitude), c.height]);
    } catch (e) { console.warn("Terrain sampling failed", e); }
  }
  return cartographics.map(c => [Cesium.Math.toDegrees(c.longitude), Cesium.Math.toDegrees(c.latitude), c.height]);
};

const {
  measureTooltips, bufferCenterCoords, bufferSourceEntity, clearMeasurements
} = useCesiumInteractions(viewer, drawMode, measureMode, isBufferMode, bufferDistance, sampleHeights);

const hasZHeight = (geom: any): boolean => {
  if (!geom || !geom.coordinates) return false;
  const check = (arr: any[]): boolean => {
    if (arr.length === 0) return false;
    if (typeof arr[0] === 'number') return arr.length >= 3 && arr[2] !== 0;
    return arr.some(item => Array.isArray(item) && check(item));
  };
  return check(geom.coordinates);
};

const getFlattened = (coords: any[], includeZ: boolean) => {
  const flat: number[] = [];
  coords.forEach(p => { flat.push(p[0], p[1]); if (includeZ) flat.push(p[2] || 0); });
  return flat;
};

const createEntitiesFromGeoJSON = (v: Cesium.Viewer, geom: any, options: any) => {
  const is3D = hasZHeight(geom);
  const style = options.style ?? {};
  const isVisible = options.show !== false;
  const entities: Cesium.Entity[] = [];

  const getColor = (css: any, fallback: string): Cesium.Color => {
    try {
      if (typeof css === 'string' && css.length > 0) {
        const color = Cesium.Color.fromCssColorString(css);
        if (Cesium.defined(color)) return color;
      }
      const fallbackColor = Cesium.Color.fromCssColorString(fallback);
      if (Cesium.defined(fallbackColor)) return fallbackColor;
    } catch (e) {
      console.warn("Color parsing failed", e);
    }
    return Cesium.Color.WHITE;
  };

  if (geom.type === 'Point' || geom.type === 'MultiPoint') {
    const all = geom.type === 'MultiPoint' ? geom.coordinates : [geom.coordinates];
    all.forEach((coords: any, idx: number) => {
      if (!coords || coords.length < 2) return;
      const pos = is3D ? Cesium.Cartesian3.fromDegrees(coords[0], coords[1], coords[2]) : Cesium.Cartesian3.fromDegrees(coords[0], coords[1]);
      const hr = is3D ? Cesium.HeightReference.NONE : Cesium.HeightReference.RELATIVE_TO_GROUND;
      const eOpt = { ...options, id: all.length > 1 ? `${options.id}-${idx}` : options.id, position: pos };
      if (style.icon?.url) {
        entities.push(v.entities.add({
          ...eOpt,
          billboard: {
            image: style.icon.url,
            scale: style.icon.scale || 1.0,
            heightReference: hr,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          }
        }));
      } else {
        entities.push(v.entities.add({
          ...eOpt,
          point: {
            pixelSize: 10,
            color: getColor(style.poly?.fillColor, '#3399CC'),
            outlineColor: Cesium.Color.WHITE,
            outlineWidth: 2,
            heightReference: hr,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          }
        }));
      }
    });
  } else if (geom.type === 'MultiLineString' || geom.type === 'LineString') {
    const all = geom.type === 'MultiLineString' ? geom.coordinates : [geom.coordinates];
    all.forEach((line: any, idx: number) => {
      if (line && line.length >= 2) {
        const flat = getFlattened(line, is3D);
        const pos = is3D ? Cesium.Cartesian3.fromDegreesArrayHeights(flat) : Cesium.Cartesian3.fromDegreesArray(flat);
        entities.push(v.entities.add({
          ...options,
          id: all.length > 1 ? `${options.id}-${idx}` : options.id,
          polyline: {
            positions: pos,
            width: style.line?.width || 2,
            material: getColor(style.line?.color, '#3399CC'),
            clampToGround: !is3D
          }
        }));
      }
    });
  } else if (geom.type === 'Polygon' || geom.type === 'MultiPolygon') {
    const all = geom.type === 'MultiPolygon' ? geom.coordinates : [geom.coordinates];
    all.forEach((poly: any, idx: number) => {
      const outer = poly[0];
      if (outer && outer.length >= 3) {
        const flat = getFlattened(outer, is3D);
        const pos = is3D ? Cesium.Cartesian3.fromDegreesArrayHeights(flat) : Cesium.Cartesian3.fromDegreesArray(flat);
        const holes = [];
        if (poly.length > 1) {
          for (let i = 1; i < poly.length; i++) {
            const hFlat = getFlattened(poly[i], is3D);
            holes.push(new Cesium.PolygonHierarchy(is3D ? Cesium.Cartesian3.fromDegreesArrayHeights(hFlat) : Cesium.Cartesian3.fromDegreesArray(hFlat)));
          }
        }
        const pId = all.length > 1 ? `${options.id}-${idx}` : options.id;
        entities.push(v.entities.add({
          ...options,
          id: pId,
          polygon: {
            hierarchy: new Cesium.PolygonHierarchy(pos, holes),
            material: getColor(style.poly?.fillColor, 'rgba(51, 153, 204, 0.4)'),
            heightReference: is3D ? Cesium.HeightReference.NONE : Cesium.HeightReference.RELATIVE_TO_GROUND,
            perPositionHeight: is3D
          }
        }));
        entities.push(v.entities.add({
          id: `${pId}-outline`,
          name: options.name,
          show: isVisible,
          polyline: {
            positions: [...pos, pos[0]],
            width: (style.line?.width || 2) + 1,
            material: getColor(style.line?.color, '#3399CC'),
            clampToGround: !is3D
          }
        }));
      }
    });
  }
  return entities;
};

const {
  isZoomHighEnough, enterGeometryEditMode, confirmGeometryEdit, exitEditMode
} = useCesiumShotFrame(viewer, projectIdRef, selectedFeatureId, selectedFeature, isGeometryEditMode, createEntitiesFromGeoJSON, sampleHeights);

// --- 3. Orchestration Logic ---
const stopActiveTool = () => { drawMode.value = null; measureMode.value = null; isBufferMode.value = false; clearMeasurements(); };
const getTooltipStyle = (pos: Cesium.Cartesian3) => {
  if (!viewer.value) return { display: 'none' };
  const win = Cesium.SceneTransforms.worldToWindowCoordinates(viewer.value.scene, pos);
  return win ? { left: win.x + 'px', top: (win.y - 15) + 'px', display: 'block' } : { display: 'none' };
};

const zoomToExtent = () => {
  const v = viewer.value;
  if (!v) return;

  // Try to use project bbox from selected project
  if (currentProject.value?.bbox && currentProject.value.bbox.type === 'Polygon') {
    const coords = (currentProject.value.bbox.coordinates as number[][][])[0];
    const lons = coords.map((c: any) => c[0]);
    const lats = coords.map((c: any) => c[1]);
    const rect = Cesium.Rectangle.fromDegrees(Math.min(...lons), Math.min(...lats), Math.max(...lons), Math.max(...lats));
    v.camera.flyTo({ destination: rect, duration: 2.0 });
    return;
  }
};

const drawPoints = ref<Cesium.Cartesian3[]>([]);
let temporaryEntity: Cesium.Entity | null = null;
let drawingHandler: Cesium.ScreenSpaceEventHandler | null = null;

watch(drawMode, (newMode) => {
  const v = viewer.value;
  if (!v) return;
  if (drawingHandler) { drawingHandler.destroy(); drawingHandler = null; }
  if (temporaryEntity) { v.entities.remove(temporaryEntity); temporaryEntity = null; }
  drawPoints.value = [];
  if (!newMode) return;
  drawingHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
  drawingType.value = newMode;
  if (newMode === 'Point') {
    drawingHandler.setInputAction(async (click: any) => {
      const pos = v.scene.pickPosition(click.position);
      if (Cesium.defined(pos)) {
        const coords = await sampleHeights([pos]);
        newObjectGeometry.value = { type: 'Point', coordinates: coords[0] };
        metadataDialog.value = true;
        drawMode.value = null;
      }
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK);
  } else {
    drawingHandler.setInputAction((click: any) => {
      const pos = v.scene.pickPosition(click.position);
      if (Cesium.defined(pos)) {
        drawPoints.value.push(pos);
        if (!temporaryEntity) {
          if (newMode === 'MultiLineString') {
            temporaryEntity = v.entities.add({
              polyline: {
                positions: new Cesium.CallbackProperty(() => {
                  return drawPoints.value.length < 2 ? [] : drawPoints.value;
                }, false) as any,
                width: 3,
                material: Cesium.Color.YELLOW,
                clampToGround: true
              }
            });
          } else {
            temporaryEntity = v.entities.add({
              polygon: {
                hierarchy: new Cesium.CallbackProperty(() => {
                  return new Cesium.PolygonHierarchy(drawPoints.value.length < 3 ? [] : drawPoints.value);
                }, false) as any,
                material: Cesium.Color.YELLOW.withAlpha(0.5)
              },
              polyline: {
                positions: new Cesium.CallbackProperty(() => {
                  return drawPoints.value.length < 2 ? [] : [...drawPoints.value, drawPoints.value[0]];
                }, false) as any,
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
      if (newMode === 'MultiLineString') newObjectGeometry.value = { type: 'MultiLineString', coordinates: [coords] };
      else newObjectGeometry.value = { type: 'Polygon', coordinates: [[...coords, coords[0]]] };
      metadataDialog.value = true;
      drawMode.value = null;
    }, Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK);
  }
});

onMounted(() => {
  if (!cesiumContainer.value) return;
  const v = new Cesium.Viewer(cesiumContainer.value, {
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    baseLayer: new Cesium.ImageryLayer(new Cesium.UrlTemplateImageryProvider({
      url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      subdomains: ['a', 'b', 'c'],
      minimumLevel: 0,
      maximumLevel: 19,
      credit: '© OpenStreetMap contributors'
    })),
    animation: false, timeline: false, baseLayerPicker: false, navigationHelpButton: false, homeButton: true, geocoder: false, sceneModePicker: true, selectionIndicator: false, infoBox: false,
  });
  viewer.value = v;
  v.scene.globe.depthTestAgainstTerrain = true;

  v.screenSpaceEventHandler.setInputAction(async (click: any) => {
    // Перехват клика для выбора точки на карте (для Viewshed и др.)
    if (store.state.geodata.pointSelectionActive) {
      const position = v.scene.pickPosition(click.position);
      if (position) {
        const cartographic = Cesium.Cartographic.fromCartesian(position);
        const longitude = Cesium.Math.toDegrees(cartographic.longitude);
        const latitude = Cesium.Math.toDegrees(cartographic.latitude);
        store.commit('geodata/SET_SELECTED_POINT', { x: longitude, y: latitude });
        store.commit('geodata/SET_POINT_SELECTION_ACTIVE', false);
      }
      return;
    }

    const picked = v.scene.pick(click.position);
    if (Cesium.defined(picked) && picked.id && picked.id.id) {
      let id = picked.id.id;
      if (id.endsWith('-outline')) id = id.replace('-outline', '');
      const match = id.match(/(.*)-[0-9]+$/);
      selectFeature({ id: match ? match[1] : id, source: 'map' });
      return;
    }
    const ray = v.camera.getPickRay(click.position);
    if (ray) {
      try {
        const feats = await (v.imageryLayers as any).pickImageryLayerFeatures(ray, v.scene);      
        if (feats && feats.length > 0) {
          const fid = feats[0].data?.id || feats[0].properties?.id || feats[0].id;
          if (fid) { selectFeature({ id: fid, source: 'map' }); return; }
        }
      } catch (e) { console.error("Selection error:", e); }
    }
    selectFeature(null);
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

  if (props.projectId) {
    initMvtLayers(props.projectId);
    setTimeout(() => { if (!initialZoomDone.value) { zoomToExtent(); store.commit('geodata/SET_INITIAL_ZOOM_DONE', true); } }, 1000);
  }
});

onUnmounted(() => { if (viewer.value) { viewer.value.destroy(); viewer.value = null; } });

watch(() => props.projectId, (newId) => {
  if (newId) {
    clearImageryLayers();
    store.commit('geodata/SET_SELECTED_PROJECT_ID', newId);
    initMvtLayers(newId);
    store.dispatch('geodata/fetchProject', newId);
    store.dispatch('geodata/fetchVectorSummaryForProject', newId);
    store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 });
    store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 });
    store.dispatch('geodata/fetchFolders', newId);
  }
}, { immediate: true });

watch([points, multilines, polygons, currentProject], (newData) => {
  if ((newData[0].length || newData[1].length || newData[2].length || currentProject.value?.bbox) && viewer.value && !initialZoomDone.value) {
    setTimeout(() => { 
      zoomToExtent(); 
      store.commit('geodata/SET_INITIAL_ZOOM_DONE', true);
    }, 800);
  }
  refreshMvtSources();
});

watch(selectedFeatureId, (newId) => {
  const v = viewer.value;
  if (!newId || !v || !autoExtentEnabled.value || lastSelectionSource.value !== 'list') return;

  if (selectedFeature.value?.bbox && selectedFeature.value.bbox.type === 'Polygon') {
    const coords = (selectedFeature.value.bbox.coordinates as number[][][])[0];
    const lons = coords.map((c: any) => c[0]);
    const lats = coords.map((c: any) => c[1]);
    const isPoint = selectedFeature.value.type === 'Point';
    
    if (isPoint) {
      v.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(lons[0], lats[0], 1500), // ~level 18
        duration: 1.5
      });
    } else {
      const rect = Cesium.Rectangle.fromDegrees(Math.min(...lons), Math.min(...lats), Math.max(...lons), Math.max(...lats));
      v.camera.flyTo({ destination: rect, duration: 1.5 });
    }
    return;
  }

  let e = v.entities.getById(newId) || v.entities.values.find(ent => ent.id.startsWith(newId));
  if (e) v.zoomTo(e);
});
</script>

<style scoped>
.cesium-container, .cesium-viewer { width: 100%; height: 100%; margin: 0; padding: 0; overflow: hidden; position: relative; }
.feature-list-card { overflow-y: auto; background-color: rgba(255, 255, 255, 0.9); }
.shot-frame-overlay {
  position: absolute; top: 15px; left: 15px; right: 15px; bottom: 15px;
  border: 2px dashed #ff5252; background-color: rgba(255, 82, 82, 0.05);
  pointer-events: none; z-index: 50; display: flex; align-items: center; justify-content: center;
}
.shot-frame-label { background-color: rgba(255, 82, 82, 0.8); color: white; padding: 4px 12px; border-radius: 4px; font-weight: bold; font-size: 12px; letter-spacing: 1px; }
.cesium-tooltip { position: absolute; background: rgba(0, 0, 0, 0.7); color: white; padding: 4px 8px; border-radius: 4px; font-size: 12px; pointer-events: none; z-index: 1002; white-space: nowrap; border: 1px solid rgba(255, 255, 255, 0.3); }
</style>
