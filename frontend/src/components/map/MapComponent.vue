<template>
  <div ref="mapParent"  class="map-container">
    <!-- Карта OpenLayers -->
    <div id="map" ref="mapContainer" class="map">

    </div>

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

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed, toRaw, shallowRef } from 'vue';
import PrintDialog from '@/components/print/PrintDialog.vue';
import { useStore } from 'vuex';
import 'ol/ol.css';
import { Map, View, Feature } from 'ol';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import TileWMS from "ol/source/TileWMS.js";
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { GeoJSON } from 'ol/format';
import { Draw, Modify } from 'ol/interaction';
import { Collection } from 'ol';
import { createEmpty, extend } from 'ol/extent';
import type { ImageryLayer, TerrainLayer, ProjectPoint, ProjectMultiline, ProjectPolygon, Status } from '@/types/api';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';
import {FullScreen} from "ol/control";
import GeodataService from '@/services/geodata.service';
import GeoObjectTree from './GeoObjectTree.vue';
import { parseStyle } from '@/util/style.util';
import { ensureMultiType } from '@/util/geo.util';

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

// --- Векторные слои для гео-объектов ---
const vectorSource = new VectorSource();
const vectorLayer = new VectorLayer({ source: vectorSource, zIndex: 100, properties: { 'willReadFrequently': true } });

// --- Состояние компонента ---
const drawMode = ref<'Point' | 'MultiLineString' | 'Polygon' | null>(null);
const isGeometryEditMode = ref(false);
let modifyInteraction: Modify | null = null;
const visibleLayerIds = ref<string[]>([]);
const selectedTerrainLayerId = ref<string | null>(null);
const activeImageLayers = shallowRef<Record<string, TileLayer<TileWMS>>>({}); // Используем shallowRef
const layerOpacities = ref<Record<string, number>>({}); // Для хранения прозрачности
const imageryMenuOpen = ref(false);
const autoExtentEnabled = ref(false);

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
const pointTypes = ['camera', 'pillar', 'other']; // Добавленные типы точек
const newObjectMetadata = ref({
  name: '',
  description: '',
  status: 'IN_PROCESS' as Status,
  type: 'other', // Default type for points
  characteristics: {} as Record<string, any> // Initialize characteristics
});
const newObjectCameraDetails = ref({ // Для хранения специфичных данных камеры
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

const handleMapClick = (event: any) => {
  if (isGeometryEditMode.value) return; // Do not process clicks in edit mode

  const feature = map?.forEachFeatureAtPixel(event.pixel, (f) => f);
  if (feature && feature.get('id') !== undefined) {
    // Ensure it's one of our vector features by checking the source
    if (vectorSource.getFeatureById(feature.get('id'))) {
      store.dispatch('geodata/selectFeature', feature.get('id'));
    }
  } else {
    store.dispatch('geodata/selectFeature', null);
  }
};

// --- Инициализация карты ---
onMounted(() => {
  if (!mapContainer.value || !mapParent.value) return;
  map = new Map({
    target: mapContainer.value,
    layers: [
      new TileLayer({ source: new OSM() }),
      vectorLayer, // Слой для всех наших гео-объектов
    ],
    view: new View({
      center: [0, 0],
      zoom: 2,
    }),
  });

  // 🔹 Fullscreen для контейнера карты
  const fullScreenControl = new FullScreen({
    source: mapParent.value, // 👈 fullscreen именно div.map
    tipLabel: 'На весь экран'
  });

  map.addControl(fullScreenControl);

  // Добавляем обработчик клика по карте
  map.on('click', handleMapClick);

  document.addEventListener('fullscreenchange', () => {
    map?.updateSize();
  });
});

// --- Очистка при размонтировании ---
onUnmounted(() => {
  if (map) {
    map.un('click', handleMapClick);
    map.setTarget(undefined);
    map = null;
  }
});

// --- Логика загрузки и отображения данных ---
const geoJsonFormat = new GeoJSON();

const clearWmsLayers = () => {
    if (!map) return;
    for (const key in activeImageLayers.value) {
        const layerProxy = activeImageLayers.value[key];
        if (layerProxy) {
            map.removeLayer(toRaw(layerProxy));
        }
    }
    activeImageLayers.value = {}; // Re-assign to empty object
    visibleLayerIds.value = [];
    layerOpacities.value = {}; // Сбрасываем прозрачность
};

const updateVectorSource = () => {
    vectorSource.clear();
    const allObjects = [...points.value, ...multilines.value, ...polygons.value];
    
    const features = allObjects.flatMap(obj => {
        // Respect visibility
        if (obj.characteristics?.visible === false) {
            return [];
        }

        // Читаем геометрию с трансформацией из 4326 (БД) в 3857 (Карта)
        const readFeatures = geoJsonFormat.readFeatures(obj.geom, {
            dataProjection: 'EPSG:4326',
            featureProjection: 'EPSG:3857'
        });
        const featureArray = Array.isArray(readFeatures) ? readFeatures : [readFeatures];
        
        featureArray.forEach(feature => {
            if (feature) {
                feature.setId(obj.id);
                feature.set('id', obj.id);
                feature.set('style', obj.characteristics?.style);
                // Применяем стиль из характеристик
                feature.setStyle(parseStyle(obj.characteristics, obj.name));
            }
        });
        
        return featureArray;
    });

    vectorSource.addFeatures(features.filter(f => f) as Feature<any>[]);
    zoomToExtent();
};

// Наблюдаем за изменением projectId
watch(() => props.projectId, (newProjectId) => {
  if (newProjectId) {
    if (map) {
      map.set('projectId', newProjectId);
    }
    clearWmsLayers(); // Очищаем старые WMS слои
    store.commit('geodata/SET_SELECTED_PROJECT_ID', newProjectId);
    store.dispatch('geodata/fetchVectorDataForProject', newProjectId);
    store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 }); // Загружаем слои
    store.dispatch('geodata/fetchTerrainLayers', { page: 0, size: 100 }); // Загружаем террейн
  }
}, { immediate: true });

// Наблюдаем за обновлением данных в store и перерисовываем карту
watch([points, multilines, polygons], updateVectorSource)

// Наблюдаем за выбором фичи и приближаемся к ней
watch(selectedFeatureId, (newId) => {
  if (!newId || !map || !autoExtentEnabled.value || !store.state.geodata.lastSelectionShouldZoom) return;
  const feature = vectorSource.getFeatureById(newId);
  if (feature) {
    const geometry = feature.getGeometry();
    if (geometry) {
      const extent = geometry.getExtent();
      map.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000, maxZoom: 18 });
    }
  }
});

const selectAndZoomToFeature = (id: string) => {
  store.dispatch('geodata/selectFeature', id);
};

// --- Логика переключения слоев ---
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
            'LAYERS': layerInfo.workspace + ":" +layerInfo.layerName,
            TILED: true,
          },
          serverType: 'geoserver',
          transition: 0,
        });
        const imageLayer = new TileLayer({ 
            source: wmsSource,
            opacity: (layerOpacities.value[layerInfo.id] || 100) / 100, // Устанавливаем начальную прозрачность
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

// --- Логика рисования ---
watch(drawMode, (newMode) => {
    if (!map) return;

    if (drawInteraction) {
        map.removeInteraction(drawInteraction);
    }

    if (newMode) {
        drawingType.value = newMode;
        drawInteraction = new Draw({
            source: vectorSource, // Рисуем на временном источнике, чтобы видеть процесс
            type: newMode,
        });

        drawInteraction.on('drawend', (event) => {
            const geometry = event.feature.getGeometry();
            if (geometry) {
                // Пишем геометрию с трансформацией из 3857 (Карта) в 4326 (БД)
                newObjectGeometry.value = geoJsonFormat.writeGeometryObject(geometry, {
                    featureProjection: 'EPSG:3857',
                    dataProjection: 'EPSG:4326'
                });
                
                // Сбрасываем метаданные и открываем диалог
                newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status, type: 'other', characteristics: {} };
                metadataDialog.value = true;
            }

            // Выключаем режим рисования
            drawMode.value = null;
        });

        map.addInteraction(drawInteraction);
    }
});

const cancelNewFeature = () => {
  metadataDialog.value = false;
  // Сбрасываем метаданные
  newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status, type: 'other', characteristics: {} };
  newObjectCameraDetails.value = { ip_address: '', port: 8000, login: '', password: '' };
};

const saveNewFeature = async () => {
    if (!newObjectGeometry.value || !props.projectId) return;

    // Формируем объект characteristics
    let characteristics = { type: newObjectMetadata.value.type };
    if (newObjectMetadata.value.type === 'camera') {
      characteristics = { ...characteristics, ...newObjectCameraDetails.value };
    }
    // Здесь также можно добавить характеристики для других типов, если они понадобятся

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
    cancelNewFeature(); // Сбрасываем форму после сохранения
};

// --- Логика редактирования геометрии ---

const enterGeometryEditMode = () => {
    if (!map || !selectedFeatureId.value) return;

    // 1. Enter edit mode state
    isGeometryEditMode.value = true;

    // 2. Get the OL feature object
    const featureToModify = vectorSource.getFeatureById(selectedFeatureId.value);
    if (!featureToModify) {
        console.error("Feature to modify not found on vector source.");
        isGeometryEditMode.value = false;
        return;
    }

    // 3. Disable other interactions
    if (drawInteraction) {
      map.removeInteraction(drawInteraction);
    }
    drawMode.value = null;

    // 4. Add Modify interaction
    modifyInteraction = new Modify({
        features: new Collection([featureToModify]),
    });
    map.addInteraction(modifyInteraction);
};

const confirmGeometryEdit = async () => {
    if (!selectedFeature.value) return;

    // Get the OpenLayers feature from the vector source, which has been mutated by the Modify interaction.
    const modifiedFeature = vectorSource.getFeatureById(selectedFeature.value.id);
    if (!modifiedFeature) {
        console.error("Could not find the feature in the vector source to confirm edit.");
        exitGeometryEditMode();
        return;
    }

    const newGeometry = modifiedFeature.getGeometry();

    if (newGeometry) {
        // Пишем геометрию с трансформацией из 3857 (Карта) в 4326 (БД)
        const newGeomAsGeoJSON = geoJsonFormat.writeGeometryObject(newGeometry, {
            featureProjection: 'EPSG:3857',
            dataProjection: 'EPSG:4326'
        });

        // Dispatch the update action to the store with the new geometry.
        await store.dispatch('geodata/updateFeature', {
            id: selectedFeature.value.id,
            type: selectedFeature.value.type,
            data: {
                name: selectedFeature.value.name, // Keep existing name
                description: selectedFeature.value.description, // Keep existing description
                geom: ensureMultiType(newGeomAsGeoJSON) // Update geometry
            }
        });
    }

    exitGeometryEditMode();
};

const cancelGeometryEdit = () => {
    exitGeometryEditMode();
    // Revert changes by refetching data
    if (props.projectId) {
        store.dispatch('geodata/fetchVectorDataForProject', props.projectId);
    }
};

const exitGeometryEditMode = () => {
    if (map && modifyInteraction) {
        map.removeInteraction(modifyInteraction);
        modifyInteraction = null;
    }
    isGeometryEditMode.value = false;
};

const zoomToExtent = () => {
  if (!map) return;
  const features = vectorSource.getFeatures();
  if (features.length === 0) return;

  const extent = createEmpty();
  features.forEach(feature => {
    const geometry = feature.getGeometry();
    if (geometry) {
        extend(extent, geometry.getExtent());
    }
  });

  if (extent && extent.every(isFinite) && (extent[0] !== Infinity)) {
    map.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000 });
  }
};

// --- File Import ---
const openImportFileDialog = () => {
  importFile.value = null;
  importFileDialog.value = true;
};

const executeFileImport = async () => {
  if (!importFile.value || !props.projectId) return;

  isImporting.value = true;
  try {
    await GeodataService.importFileToProject(props.projectId, importFile.value);
    // Refresh data
    await store.dispatch('geodata/fetchVectorDataForProject', props.projectId);
    importFileDialog.value = false;
  } catch (error) {
    console.error("Import failed:", error);
    // You might want to show an error notification here
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
  top: 250px; /* Позиция под переключателем слоев */
  right: 100px;
  width: 100px;
}

.top-right-objects {
    top: 250px; /* Позиция под переключателем слоев */
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
  bottom: 80px; /* Position above the drawing tools */
  right: 10px;
  background-color: transparent !important;
}
</style>
