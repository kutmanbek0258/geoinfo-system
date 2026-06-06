<template>
  <div style="height: calc(100vh - 64px); width: 100%; position: relative;">
    <div v-if="projectId" class="h-100 w-100">
      <MapComponentMVT v-if="mode === '2D'" :project-id="projectId" />
      <CesiumMapComponent v-else :project-id="projectId" />
    </div>

    <!-- Переключатель режимов (2D / 3D) -->
    <div v-if="projectId" class="map-mode-toggle">
      <v-btn-toggle v-model="mode" mandatory color="primary" density="compact">
        <v-btn value="2D" title="2D View (OpenLayers)">2D</v-btn>
        <v-btn value="3D" title="3D View (Cesium)">3D</v-btn>
      </v-btn-toggle>
    </div>
    
    <div v-else-if="!projectId" class="d-flex justify-center align-center h-100">
        <v-progress-circular indeterminate color="primary"></v-progress-circular>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MapComponentMVT from '@/components/map/MapComponentMVT.vue';
import CesiumMapComponent from '@/components/map/CesiumMapComponent.vue';

const route = useRoute();
const router = useRouter();
const projectId = route.params.id as string;
const mode = ref(route.query.mode as string || '2D');

watch(mode, (newVal) => {
  router.replace({
    query: {
      ...route.query,
      mode: newVal
    }
  });
});
</script>

<style scoped>
.map-mode-toggle {
  position: absolute;
  top: 16px;
  right: 180px; /* Позиция слева от кнопки Imagery Layers / Fullscreen */
  z-index: 1000;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  padding: 2px;
}
</style>

