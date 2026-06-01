<template>
  <div style="height: calc(100vh - 64px); width: 100%; position: relative;">
    <div v-if="projectId" class="h-100 w-100">
      <template v-if="mode === '2D'">
        <MapComponentMVT v-if="useMvt" :project-id="projectId" />
        <MapComponent v-else :project-id="projectId" />
      </template>
      <CesiumMapComponent v-else :project-id="projectId" />
    </div>

    <!-- Переключатель режимов рендеринга (MVT / GeoJSON) -->
    <div v-if="mode === '2D' && projectId" class="rendering-mode-toggle">
      <v-btn-toggle v-model="renderingMode" mandatory color="primary" density="compact">
        <v-btn value="geojson" title="GeoJSON Rendering">GeoJSON</v-btn>
        <v-btn value="mvt" title="MVT Rendering">MVT (Тайлы)</v-btn>
      </v-btn-toggle>
    </div>
    
    <div v-else-if="!projectId" class="d-flex justify-center align-center h-100">
        <v-progress-circular indeterminate color="primary"></v-progress-circular>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MapComponent from '@/components/map/MapComponent.vue';
import MapComponentMVT from '@/components/map/MapComponentMVT.vue';
import CesiumMapComponent from '@/components/map/CesiumMapComponent.vue';

const route = useRoute();
const router = useRouter();
const projectId = route.params.id as string;
const mode = ref(route.query.mode as string || '2D');

// По умолчанию используем MVT рендеринг, если в query-параметрах явно не указано mvt=false
const renderingMode = ref(route.query.mvt !== 'false' ? 'mvt' : 'geojson');
const useMvt = computed(() => renderingMode.value === 'mvt');

watch(renderingMode, (newVal) => {
  router.replace({
    query: {
      ...route.query,
      mvt: newVal === 'mvt' ? 'true' : 'false'
    }
  });
});
</script>

<style scoped>
.rendering-mode-toggle {
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

