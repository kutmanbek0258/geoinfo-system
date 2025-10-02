<template>
  <div style="height: calc(100vh - 64px); width: 100%;"> <!-- 64px - примерная высота стандартного хедера -->
    <MapComponent v-if="projectId" :project-id="projectId" />
    <div v-else class="d-flex justify-center align-center h-100">
        <v-progress-circular indeterminate color="primary"></v-progress-circular>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';
import MapComponent from '@/components/map/MapComponent.vue';

const route = useRoute();
const projectId = route.params.id as string;
</script>

<style scoped>
/* Стили, если нужны */
</style>
