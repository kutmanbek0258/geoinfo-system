<template>
  <div style="height: calc(100vh - 64px); width: 100%; position: relative;">
    <div v-if="projectId" class="h-100 w-100">
      <MapComponent v-if="mode === '2D'" :project-id="projectId" />
      <CesiumMapComponent v-else :project-id="projectId" />
    </div>
    
    <div v-else class="d-flex justify-center align-center h-100">
        <v-progress-circular indeterminate color="primary"></v-progress-circular>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import MapComponent from '@/components/map/MapComponent.vue';
import CesiumMapComponent from '@/components/map/CesiumMapComponent.vue';

const route = useRoute();
const projectId = route.params.id as string;
const mode = ref(route.query.mode as string || '2D');
</script>

<style scoped>
/* Стили, если нужны */
</style>
