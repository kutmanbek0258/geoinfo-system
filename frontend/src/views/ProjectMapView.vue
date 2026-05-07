<template>
  <div style="height: calc(100vh - 64px); width: 100%; position: relative;">
    <!-- Переключатель 2D/3D -->
    <v-btn-toggle
      v-model="mode"
      mandatory
      style="position: absolute; top: 10px; right: 10px; z-index: 1000;"
    >
      <v-btn value="2D">2D</v-btn>
      <v-btn value="3D">3D</v-btn>
    </v-btn-toggle>

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
const mode = ref('2D');
</script>

<style scoped>
/* Стили, если нужны */
</style>
