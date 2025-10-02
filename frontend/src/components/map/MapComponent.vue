<template>
  <div class="map-container">
    <!-- Карта OpenLayers -->
    <div id="map" ref="mapContainer" class="map"></div>

    <!-- Оверлей 1: Переключатель слоев -->
    <v-card class="map-overlay top-right-layers">
      <v-card-title>Imagery Layers</v-card-title>
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

    <!-- Оверлей 2: Список Гео-объектов -->
    <v-card class="map-overlay top-right-objects">
        <v-card-title>Geo-Objects</v-card-title>
        <v-list dense>
            <v-list-item v-for="point in points" :key="point.id" :title="point.name || 'Point'"></v-list-item>
            <v-list-item v-for="line in multilines" :key="line.id" :title="line.name || 'Line'"></v-list-item>
            <v-list-item v-for="polygon in polygons" :key="polygon.id" :title="polygon.name || 'Polygon'"></v-list-item>
        </v-list>
    </v-card>

    <!-- Оверлей 3: Кнопки добавления -->
    <div class="map-overlay bottom-right">
      <v-btn-toggle v-model="drawMode" variant="elevated" density="comfortable">
        <v-btn value="Point" title="Add Point">
          <v-icon>mdi-map-marker</v-icon>
        </v-btn>
        <v-btn value="LineString" title="Add Line">
          <v-icon>mdi-vector-polyline</v-icon>
        </v-btn>
        <v-btn value="Polygon" title="Add Polygon">
          <v-icon>mdi-vector-polygon</v-icon>
        </v-btn>
      </v-btn-toggle>
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
import { Map, View } from 'ol';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import ImageLayer from 'ol/layer/Image';
import ImageWMS from 'ol/source/ImageWMS';
import { GeoJSON } from 'ol/format';
import { Draw } from 'ol/interaction';
import type { ImageryLayer, ProjectPoint, ProjectMultiline, ProjectPolygon, Status } from '@/types/api';

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
const vectorLayer = new VectorLayer({ source: vectorSource });

// --- Состояние компонента ---
const drawMode = ref<'Point' | 'LineString' | 'Polygon' | null>(null);
const visibleLayerIds = ref<string[]>([]);
const activeImageLayers = ref<Map<string, ImageLayer<ImageWMS>>>(new Map());

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
        new TileLayer({ source: new OSM() }),
        vectorLayer, // Слой для всех наших гео-объектов
      ],
      view: new View({
        center: [0, 0],
        zoom: 2,
      }),
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
    const features = [
        ...points.value.map(p => geoJsonFormat.readFeature(p.geom)),
        ...multilines.value.map(l => geoJsonFormat.readFeature(l.geom)),
        ...polygons.value.map(p => geoJsonFormat.readFeature(p.geom)),
    ];
    vectorSource.addFeatures(features);
};

// Наблюдаем за изменением projectId
watch(() => props.projectId, (newProjectId) => {
  if (newProjectId) {
    store.dispatch('geodata/fetchVectorDataForProject', newProjectId);
    store.dispatch('geodata/fetchImageryLayers', { page: 0, size: 100 }); // Загружаем слои
  }
}, { immediate: true });

// Наблюдаем за обновлением данных в store и перерисовываем карту
watch([points, multilines, polygons], updateVectorSource);

// --- Логика переключения слоев ---
const toggleImageryLayer = (layerInfo: ImageryLayer, event: any) => {
    if (!map) return;
    const isVisible = event.target.checked;

    if (isVisible) {
        const wmsSource = new ImageWMS({
            url: layerInfo.serviceUrl,
            params: { 'LAYERS': layerInfo.layerName },
            serverType: 'geoserver',
        });
        const imageLayer = new ImageLayer({ source: wmsSource });
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

    switch (drawingType.value) {
        case 'Point':
            await store.dispatch('geodata/createPoint', payload);
            break;
        case 'LineString':
            await store.dispatch('geodata/createMultiline', payload);
            break;
        case 'Polygon':
            await store.dispatch('geodata/createPolygon', payload);
            break;
    }

    metadataDialog.value = false;
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

.top-right-objects {
    top: 250px; /* Позиция под переключателем слоев */
    right: 10px;
    width: 250px;
}

.bottom-right {
  bottom: 20px;
  right: 10px;
  background-color: transparent !important;
}
</style>
