<script setup lang="ts">
import { ref, onMounted } from "vue";
import Map from "ol/Map.js";
import View from "ol/View.js";
import TileWMS from "ol/source/TileWMS.js";
import OSM from "ol/source/OSM.js";
import VectorSource from "ol/source/Vector";
import Feature from "ol/Feature";
import Point from "ol/geom/Point";
import { Tile as TileLayer, Vector as VectorLayer } from "ol/layer";
import { Style, Stroke, Fill, Circle as CircleStyle } from "ol/style";
import { fromLonLat } from "ol/proj.js";

// ссылка на div для карты
const mapContainer = ref<HTMLDivElement | null>(null);

// --- Источник с объектами ---
const vectorSource = new VectorSource();

// --- Точка с координатами ---
const pointFeature = new Feature({
  geometry: new Point(fromLonLat([73.839085, 42.820890])) // преобразуем в EPSG:3857
});
pointFeature.setStyle(
    new Style({
      image: new CircleStyle({
        radius: 7,
        fill: new Fill({ color: "blue" }),
        stroke: new Stroke({ color: "white", width: 2 })
      })
    })
);

vectorSource.addFeature(pointFeature);

// --- Слой с вектором ---
const vectorLayer = new VectorLayer({
  source: vectorSource
});

onMounted(() => {
  if (!mapContainer.value) return;

  const map = new Map({
    target: mapContainer.value,
    layers: [
      // базовый слой OSM
      new TileLayer({
        source: new OSM(),
      }),

      // WMS слой из GeoServer
      new TileLayer({
        source: new TileWMS({
          url: "http://localhost:8085/geoserver/wms", // твой GeoServer
          params: {
            LAYERS: "etibakir:kara-balta", // имя слоя в GeoServer
            TILED: true,
          },
          serverType: "geoserver",
          transition: 0,
        }),
      }),
      vectorLayer,
    ],
    view: new View({
      center: fromLonLat([74.6, 42.9]), // координаты (пример для Кыргызстана)
      zoom: 7,
    }),
  });
});
</script>

<template>
    <v-row>
      <div ref="mapContainer" class="map"></div>
    </v-row>
</template>
<style>
.map {
  width: 100%;
  height: 100vh;
}
</style>
