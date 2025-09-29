<template>
  <div ref="mapContainer" class="map-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import 'ol/ol.css';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { GeoJSON } from 'ol/format';
import { Feature } from 'ol';
import { Geometry } from 'ol/geom';

const props = defineProps<{
  features: {
    points: any[];
    lines: any[];
    polygons: any[];
  };
}>();

const emit = defineEmits(['feature-selected']);

const mapContainer = ref<HTMLElement | null>(null);
let map: Map | null = null;
let vectorSource = new VectorSource();

// --- Map Initialization ---
onMounted(() => {
  if (!mapContainer.value) return;

  vectorSource = new VectorSource();
  const vectorLayer = new VectorLayer({
    source: vectorSource,
  });

  map = new Map({
    target: mapContainer.value,
    layers: [
      new TileLayer({
        source: new OSM(),
      }),
      vectorLayer,
    ],
    view: new View({
      center: [0, 0],
      zoom: 2,
    }),
  });

  map.on('click', (event) => {
    map?.forEachFeatureAtPixel(event.pixel, (feature) => {
      const id = feature.get('id');
      const type = feature.get('type');
      emit('feature-selected', { id, type });
    });
  });
});

// --- Feature Drawing ---
watch(() => props.features, (newFeatures) => {
  vectorSource.clear();
  if (!newFeatures) return;

  const geoJsonFormat = new GeoJSON({
      featureProjection: 'EPSG:3857' // Project map data to Web Mercator
  });

  const addFeatures = (featuresToAdd: any[], type: string) => {
    if (featuresToAdd && featuresToAdd.length > 0) {
        const olFeatures = geoJsonFormat.readFeatures({
            type: 'FeatureCollection',
            features: featuresToAdd
        });
        olFeatures.forEach(f => f.set('type', type)); // Add type for click handler
        vectorSource.addFeatures(olFeatures);
    }
  };

  addFeatures(newFeatures.points, 'points');
  addFeatures(newFeatures.lines, 'lines');
  addFeatures(newFeatures.polygons, 'polygons');
  
  // Zoom to extent of new features
  if (vectorSource.getFeatures().length > 0) {
      map?.getView().fit(vectorSource.getExtent(), { padding: [50, 50, 50, 50], duration: 1000 });
  }

}, { deep: true });

</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  background-color: #f0f0f0;
}
</style>
