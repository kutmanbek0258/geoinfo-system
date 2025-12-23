<template>
  <div class="map-container">
    <!-- Карта OpenLayers -->
    <div id="map" ref="mapContainer" class="map"></div>

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
    </v-card>

<!--    &lt;!&ndash; Оверлей 2: Список Гео-объектов &ndash;&gt;-->
<!--    <v-card class="map-overlay top-right-objects">-->
<!--        <v-card-title>Geo-Objects</v-card-title>-->
<!--        <v-list dense>-->
<!--            <v-list-item v-for="point in points" :key="point.id" :title="point.name || 'Point'"></v-list-item>-->
<!--            <v-list-item v-for="line in multilines" :key="line.id" :title="line.name || 'Line'"></v-list-item>-->
<!--            <v-list-item v-for="polygon in polygons" :key="polygon.id" :title="polygon.name || 'Polygon'"></v-list-item>-->
<!--        </v-list>-->
<!--    </v-card>-->

    <!-- Оверлей 3: Кнопки добавления -->
    <div class="map-overlay bottom-right">
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

    <!-- Оверлей 4: Детали объекта -->
    <div class="map-overlay top-right-details">
        <ObjectDetails
            v-if="selectedFeatureId && !isGeometryEditMode"
            :feature-id="selectedFeatureId"
            :feature-name="selectedFeature?.name"
            :feature-description="selectedFeature?.description"
            :feature-type="selectedFeature?.type || ''"
            :feature-image-url="selectedFeature?.imageUrl"
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
    </div>

    <!-- Диалог для ввода метаданных нового объекта -->
    <v-dialog v-model="metadataDialog" max-width="500px">
        <v-card>
            <v-card-title>New {{ drawingType }}</v-card-title>
            <v-card-text>
                <v-text-field v-model="newObjectMetadata.name" label="Name" required></v-text-field>
                <v-textarea v-model="newObjectMetadata.description" label="Description"></v-textarea>
                <v-select v-model="newObjectMetadata.status" :items="['COMPLETED', 'IN_PROCESS', 'REJECTED']" label="Status" required></v-select>
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
import { ref, onMounted, onUnmounted, watch, computed, toRaw } from 'vue';
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
import type { ImageryLayer, ProjectPoint, ProjectMultiline, ProjectPolygon, Status } from '@/types/api';
import ObjectDetails from './ObjectDetails.vue';
import SearchComponent from '@/components/search/SearchComponent.vue';

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
const activeImageLayers = ref<Record<string, TileLayer<TileWMS>>>({}); // Используем plain object
const layerOpacities = ref<Record<string, number>>({}); // Для хранения прозрачности
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
const newObjectMetadata = ref({ name: '', description: '', status: 'IN_PROCESS' as Status });

// --- Данные из Vuex ---
const imageryLayers = computed<ImageryLayer[]>(() => store.state.geodata.imageryLayers?.content || []);
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
  if (mapContainer.value) {
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

    // Добавляем обработчик клика по карте
    map.on('click', handleMapClick);
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
        const readFeatures = geoJsonFormat.readFeature(obj.geom);
        const featureArray = Array.isArray(readFeatures) ? readFeatures : [readFeatures];
        
        featureArray.forEach(feature => {
            if (feature) {
                feature.setId(obj.id); // Устанавливаем внутренний ID для OpenLayers
                feature.set('id', obj.id); // Устанавливаем ID в свойства фичи для обратной совместимости
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
    clearWmsLayers(); // Очищаем старые WMS слои
    store.commit('geodata/SET_SELECTED_PROJECT_ID', newProjectId);
    store.dispatch('geodata/fetchVectorDataForProject', newProjectId);
    store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 }); // Загружаем слои
  }
}, { immediate: true });

// Наблюдаем за обновлением данных в store и перерисовываем карту
watch([points, multilines, polygons], updateVectorSource)

// Наблюдаем за выбором фичи и приближаемся к ней
watch(selectedFeatureId, (newId) => {
  if (!newId || !map) return;
  const feature = vectorSource.getFeatureById(newId);
  if (feature) {
    const geometry = feature.getGeometry();
    if (geometry) {
      const extent = geometry.getExtent();
      map.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000 });
    }
  }
});

// --- Логика переключения слоев ---
const setLayerOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageLayers.value[layerId];
    if (layer) {
        toRaw(layer).setOpacity(opacity / 100);
        layerOpacities.value[layerId] = opacity;
    }
};

const toggleImageryLayer = (layerInfo: ImageryLayer, event: any) => {
    if (!map) return;
    const isVisible = event.target.checked;

    if (isVisible) {
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
        activeImageLayers.value[layerInfo.id] = imageLayer;
        if (layerOpacities.value[layerInfo.id] === undefined) {
            layerOpacities.value[layerInfo.id] = 100;
        }
    } else {
        const layerToRemove = activeImageLayers.value[layerInfo.id];
        if (layerToRemove) {
            map.removeLayer(toRaw(layerToRemove));
            delete activeImageLayers.value[layerInfo.id];
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
                newObjectGeometry.value = geoJsonFormat.writeGeometryObject(geometry);
                
                // Сбрасываем метаданные и открываем диалог
                newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status };
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
    // Можно опционально удалить фичу с карты, если она рисовалась на отдельном слое
};

const saveNewFeature = async () => {
    if (!newObjectGeometry.value || !props.projectId) return;

    const payload = {
        projectId: props.projectId,
        geom: newObjectGeometry.value,
        ...newObjectMetadata.value
    };

    await store.dispatch('geodata/createFeature', { type: drawingType.value, data: payload });

    metadataDialog.value = false;
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
        const newGeomAsGeoJSON = geoJsonFormat.writeGeometryObject(newGeometry);

        // Dispatch the update action to the store with the new geometry.
        await store.dispatch('geodata/updateFeature', {
            id: selectedFeature.value.id,
            type: selectedFeature.value.type,
            data: {
                name: selectedFeature.value.name, // Keep existing name
                description: selectedFeature.value.description, // Keep existing description
                geom: newGeomAsGeoJSON // Update geometry
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
  top: 10px;
  right: 10px;
  width: 250px;
}

.top-left-search {
  top: 10px;
  left: 10px;
  width: 300px;
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
    top: 10px;
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
