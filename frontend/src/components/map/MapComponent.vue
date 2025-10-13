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
          ></v-checkbox>
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
            v-if="selectedFeatureId"
            :feature-id="selectedFeatureId"
            :feature-name="selectedFeature?.name"
            :feature-description="selectedFeature?.description"
            :feature-type="selectedFeature?.type"
            :feature-image-url="selectedFeature?.imageUrl"
        />
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
                <v-btn text @click="cancelNewFeature">Cancel</v-btn>
                <v-btn color="primary" @click="saveNewFeature">Save</v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue';
import { useStore } from 'vuex';
import 'ol/ol.css';
import { Map, View, Feature } from 'ol';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import TileWMS from "ol/source/TileWMS.js";
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import ImageLayer from 'ol/layer/Image';
import ImageWMS from 'ol/source/ImageWMS';
import { GeoJSON } from 'ol/format';
import { Draw } from 'ol/interaction';
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
const visibleLayerIds = ref<string[]>([]);
const activeImageLayers = ref<Map<string, ImageLayer<ImageWMS>>>(new Map());
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

// --- Инициализация карты ---
onMounted(() => {
  if (mapContainer.value) {
    map = new Map({
      target: mapContainer.value,
      layers: [
        vectorLayer, // Слой для всех наших гео-объектов
        new TileLayer({ source: new OSM() }),
      ],
      view: new View({
        center: [0, 0],
        zoom: 2,
      }),
    });

    // Добавляем обработчик клика по карте
    map.on('click', (event) => {
      const feature = map?.forEachFeatureAtPixel(event.pixel, (f) => f);
      if (feature) {
        store.dispatch('geodata/selectFeature', feature.get('id')); // Получаем ID из свойств фичи
      } else {
        store.dispatch('geodata/selectFeature', null); // Сбрасываем выбор, если клик был по пустой области
      }
    });

  }
});

// --- Очистка при размонтировании ---
onUnmounted(() => {
  if (map) {
    map.setTarget(undefined);
    map = null;
  }
});

// --- Логика загрузки и отображения данных ---
const geoJsonFormat = new GeoJSON();

const updateVectorSource = () => {
    vectorSource.clear();
    const allObjects = [...points.value, ...multilines.value, ...polygons.value];
    
    const features = allObjects.map(obj => {
        const feature = geoJsonFormat.readFeature(obj.geom);
        feature.setId(obj.id); // Устанавливаем внутренний ID для OpenLayers
        feature.set('id', obj.id); // Устанавливаем ID в свойства фичи для обратной совместимости
        return feature;
    });

    vectorSource.addFeatures(features);
    zoomToExtent();
};

// Наблюдаем за изменением projectId
watch(() => props.projectId, (newProjectId) => {
  if (newProjectId) {
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
    const extent = feature.getGeometry().getExtent();
    map.getView().fit(extent, { padding: [100, 100, 100, 100], duration: 2000 });
  }
});

// --- Логика переключения слоев ---
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
        const imageLayer = new TileLayer({ source: wmsSource });
        map.addLayer(imageLayer);
        activeImageLayers.value.set(layerInfo.id, imageLayer);
    } else {
        const layerToRemove = activeImageLayers.value.get(layerInfo.id);
        if (layerToRemove) {
            map.removeLayer(layerToRemove);
            activeImageLayers.value.delete(layerInfo.id);
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
            newObjectGeometry.value = geoJsonFormat.writeGeometryObject(geometry);
            
            // Сбрасываем метаданные и открываем диалог
            newObjectMetadata.value = { name: '', description: '', status: 'IN_PROCESS' as Status };
            metadataDialog.value = true;

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

const zoomToExtent = () => {
  if (!map) return;
  const features = vectorSource.getFeatures();
  if (features.length === 0) return;

  const extent = createEmpty();
  features.forEach(feature => {
    extend(extent, feature.getGeometry().getExtent());
  });

  if (extent && extent.every(isFinite)) {
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
</style>
