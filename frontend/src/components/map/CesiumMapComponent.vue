<template>
  <div ref="cesiumParent" class="cesium-container">
    <!-- Карта Cesium -->
    <div ref="cesiumContainer" class="cesium-viewer"></div>

    <!-- Оверлей 1: Переключатель слоев -->
    <v-card class="map-overlay top-right-layers">
      <v-card-title class="d-flex align-center">
        <span>Imagery Layers</span>
        <v-spacer></v-spacer>
        <v-btn icon="mdi-magnify-scan" variant="text" @click="zoomToExtent" title="Zoom to extent"></v-btn>
      </v-card-title>
      <v-list dense>
        <v-list-item v-for="layer in imageryLayers" :key="layer.id">
          <v-checkbox
            :label="layer.name"
            :value="layer.id"
            v-model="visibleLayerIds"
            @change="toggleImageryLayer(layer, $event)"
            hide-details
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
            class="mt-n2"
          ></v-slider>
        </v-list-item>
      </v-list>

      <v-divider></v-divider>
      <v-card-title>Terrain Layers</v-card-title>
      <v-radio-group v-model="selectedTerrainId" hide-details class="px-4 pb-4">
        <v-radio label="World Terrain" :value="null"></v-radio>
        <v-radio v-for="layer in terrainLayers" :key="layer.id" :label="layer.title" :value="layer.id"></v-radio>
      </v-radio-group>
    </v-card>

    <!-- Оверлей 3: Кнопки добавления -->
    <div class="map-overlay bottom-right d-flex flex-column align-end">
      <v-btn
        icon="mdi-file-import"
        color="primary"
        class="mb-2"
        @click="openImportFileDialog"
        title="Import KML/KMZ to this project"
      ></v-btn>
      
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

  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, computed, toRaw } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
import type { ImageryLayer, TerrainLayer, ProjectPoint, ProjectMultiline, ProjectPolygon, Status } from '@/types/api';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';
import GeodataService from '@/services/geodata.service';
import { parseStyle } from '@/util/style.util';

const props = defineProps<{
  projectId: string;
}>();

const store = useStore();
const cesiumContainer = ref<HTMLElement | null>(null);
const cesiumParent = ref<HTMLElement | null>(null);
let viewer: Cesium.Viewer | null = null;

// --- Состояние компонента ---
const drawMode = ref<'Point' | 'MultiLineString' | 'Polygon' | null>(null);
const isGeometryEditMode = ref(false);
const visibleLayerIds = ref<string[]>([]);
const activeImageryLayers = ref<Record<string, Cesium.ImageryLayer>>({});
const layerOpacities = ref<Record<string, number>>({});

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

// --- Вспомогательные функции ---

const clearImageryLayers = () => {
  const v = viewer;
  if (!v) return;
  for (const id in activeImageryLayers.value) {
    v.imageryLayers.remove(activeImageryLayers.value[id]);
  }
  activeImageryLayers.value = {};
  visibleLayerIds.value = [];
  layerOpacities.value = {};
};

const toggleImageryLayer = (layerInfo: ImageryLayer, event: any) => {
  const v = viewer;
  if (!v) return;
  const isVisible = event.target.checked;

  if (isVisible) {
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
    activeImageryLayers.value[layerInfo.id] = layer;
    if (layerOpacities.value[layerInfo.id] === undefined) {
      layerOpacities.value[layerInfo.id] = 100;
    }
  } else {
    const layer = activeImageryLayers.value[layerInfo.id];
    if (layer) {
      v.imageryLayers.remove(layer);
      delete activeImageryLayers.value[layerInfo.id];
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

const updateVectorSource = () => {
  const v = viewer;
  if (!v) return;
  v.entities.removeAll();

  const allObjects = [...points.value, ...multilines.value, ...polygons.value];

  allObjects.forEach(obj => {
    const style = obj.characteristics?.style || {};
    let entityOptions: Cesium.Entity.ConstructorOptions = {
      id: obj.id,
      name: obj.name,
      description: obj.description,
    };

    if (obj.geom.type === 'Point') {
      const coords = obj.geom.coordinates;
      entityOptions.position = Cesium.Cartesian3.fromDegrees(coords[0], coords[1]);
      
      if (style.icon?.url) {
        entityOptions.billboard = {
          image: style.icon.url,
          scale: style.icon.scale || 1.0,
          heightReference: Cesium.HeightReference.RELATIVE_TO_GROUND,
        };
      } else {
        entityOptions.point = {
          pixelSize: 10,
          color: Cesium.Color.fromCssColorString(style.poly?.fillColor || '#3399CC'),
          outlineColor: Cesium.Color.WHITE,
          outlineWidth: 2,
          heightReference: Cesium.HeightReference.RELATIVE_TO_GROUND,
        };
      }
    } else if (obj.geom.type === 'MultiLineString' || obj.geom.type === 'LineString') {
      const lineCoords = obj.geom.type === 'MultiLineString' ? obj.geom.coordinates[0] : obj.geom.coordinates;
      if (lineCoords && lineCoords.length >= 2) {
        const flattened = lineCoords.flat();
        if (flattened.length >= 4 && flattened.length % 2 === 0) {
          const positions = Cesium.Cartesian3.fromDegreesArray(flattened);
          entityOptions.polyline = {
            positions: positions,
            width: style.line?.width || 2,
            material: Cesium.Color.fromCssColorString(style.line?.color || '#3399CC'),
            clampToGround: true,
          };
        }
      }
    } else if (obj.geom.type === 'Polygon') {
      const outerRing = obj.geom.coordinates[0];
      if (outerRing && outerRing.length >= 3) {
        const flattened = outerRing.flat();
        if (flattened.length >= 6 && flattened.length % 2 === 0) {
          const positions = Cesium.Cartesian3.fromDegreesArray(flattened);
          entityOptions.polygon = {
            hierarchy: new Cesium.PolygonHierarchy(positions),
            material: Cesium.Color.fromCssColorString(style.poly?.fillColor || 'rgba(51, 153, 204, 0.4)'),
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
          };
          v.entities.add({
            polyline: {
              positions: positions.concat(positions[0]),
              width: style.line?.width || 2,
              material: Cesium.Color.fromCssColorString(style.line?.color || '#3399CC'),
              clampToGround: true,
            }
          });
        }
      }
    }

    v.entities.add(entityOptions);
  });
};

const saveNewFeature = async () => {
  if (!newObjectGeometry.value || !props.projectId) return;

  let characteristics = { type: newObjectMetadata.value.type };
  if (newObjectMetadata.value.type === 'camera') {
    characteristics = { ...characteristics, ...newObjectCameraDetails.value };
  }

  const payload = {
    projectId: props.projectId,
    geom: newObjectGeometry.value,
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

const editPoints = ref<Cesium.Cartesian3[]>([]);
const draggerEntities = ref<Cesium.Entity[]>([]);
let activeDragger: Cesium.Entity | null = null;
let editHandler: Cesium.ScreenSpaceEventHandler | null = null;

const enterGeometryEditMode = () => {
  const v = viewer;
  if (!v || !selectedFeature.value) return;

  isGeometryEditMode.value = true;
  const entity = v.entities.getById(selectedFeature.value.id);
  if (entity) entity.show = false; // Hide original

  if (selectedFeature.value.type === 'Point' && entity?.position) {
    const pos = entity.position.getValue(Cesium.JulianDate.now());
    if (pos) editPoints.value = [pos];
  } else if (selectedFeature.value.type === 'MultiLineString' && entity?.polyline?.positions) {
    const positions = entity.polyline.positions.getValue(Cesium.JulianDate.now()) as Cesium.Cartesian3[];
    if (positions) editPoints.value = [...positions];
  } else if (selectedFeature.value.type === 'Polygon' && entity?.polygon?.hierarchy) {
    const hierarchy = entity.polygon.hierarchy.getValue(Cesium.JulianDate.now()) as Cesium.PolygonHierarchy;
    if (hierarchy) editPoints.value = [...hierarchy.positions];
  }

  editPoints.value.forEach((pos, index) => {
    const dragger = v.entities.add({
      position: new Cesium.CallbackPositionProperty(() => editPoints.value[index], false) as any,
      point: {
        pixelSize: 12,
        color: Cesium.Color.RED,
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
    });
    (dragger as any).userData = { index };
    draggerEntities.value.push(dragger);
  });

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
        const index = (activeDragger as any).userData.index;
        editPoints.value[index] = position;
      }
    }
  }, Cesium.ScreenSpaceEventType.MOUSE_MOVE);

  editHandler.setInputAction(() => {
    activeDragger = null;
    v.scene.screenSpaceCameraController.enableRotate = true;
  }, Cesium.ScreenSpaceEventType.LEFT_UP);
};

const exitEditMode = () => {
  const v = viewer;
  if (editHandler) {
    editHandler.destroy();
    editHandler = null;
  }
  if (v) {
    draggerEntities.value.forEach(e => v.entities.remove(e));
  }
  draggerEntities.value = [];
  if (selectedFeature.value && v) {
    const entity = v.entities.getById(selectedFeature.value.id);
    if (entity) entity.show = true;
  }
  isGeometryEditMode.value = false;
};

const confirmGeometryEdit = async () => {
  if (!selectedFeature.value) return;

  const coords = editPoints.value.map(p => {
    const carto = Cesium.Cartographic.fromCartesian(p);
    return [Cesium.Math.toDegrees(carto.longitude), Cesium.Math.toDegrees(carto.latitude)];
  });

  let newGeomAsGeoJSON;
  if (selectedFeature.value.type === 'Point') {
    newGeomAsGeoJSON = { type: 'Point', coordinates: coords[0] };
  } else if (selectedFeature.value.type === 'MultiLineString') {
    newGeomAsGeoJSON = { type: 'MultiLineString', coordinates: [coords] };
  } else {
    newGeomAsGeoJSON = { type: 'Polygon', coordinates: [[...coords, coords[0]]] };
  }

  await store.dispatch('geodata/updateFeature', {
    id: selectedFeature.value.id,
    type: selectedFeature.value.type,
    data: {
      name: selectedFeature.value.name,
      description: selectedFeature.value.description,
      geom: newGeomAsGeoJSON
    }
  });

  exitEditMode();
};

const cancelGeometryEdit = () => {
  exitEditMode();
};

// --- Watchers ---

watch(() => props.projectId, (newId) => {
  if (newId) {
    clearImageryLayers();
    store.commit('geodata/SET_SELECTED_PROJECT_ID', newId);
    store.dispatch('geodata/fetchVectorDataForProject', newId);
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

watch([points, multilines, polygons], updateVectorSource);

watch(selectedFeatureId, (newId) => {
  const v = viewer;
  if (!newId || !v) return;
  const entity = v.entities.getById(newId);
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
    drawingHandler.setInputAction((click: any) => {
      const position = v.scene.pickPosition(click.position);
      if (Cesium.defined(position)) {
        const cartographic = Cesium.Cartographic.fromCartesian(position);
        newObjectGeometry.value = {
          type: 'Point',
          coordinates: [
            Cesium.Math.toDegrees(cartographic.longitude),
            Cesium.Math.toDegrees(cartographic.latitude)
          ]
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

    drawingHandler.setInputAction(() => {
      if (drawPoints.value.length < 2) return;

      const coords = drawPoints.value.map(p => {
        const carto = Cesium.Cartographic.fromCartesian(p);
        return [Cesium.Math.toDegrees(carto.longitude), Cesium.Math.toDegrees(carto.latitude)];
      });

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
    animation: false,
    timeline: false,
    baseLayerPicker: true,
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
        store.dispatch('geodata/selectFeature', pickedObject.id.id);
    } else {
        store.dispatch('geodata/selectFeature', null);
    }
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);
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
  width: 250px;
}

.top-left-search {
  top: 60px;
  left: 10px;
  width: 350px;
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
</style>
