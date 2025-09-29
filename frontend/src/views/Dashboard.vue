<template>
  <v-layout>
    <!-- Левая панель -->
    <v-navigation-drawer permanent width="300">
      <LeftSidebar
        @project-selected="handleProjectSelected"
        @feature-selected="handleFeatureSelected"
      />
    </v-navigation-drawer>

    <!-- Основной контент с картой -->
    <v-main>
      <MapComponent
        :features="features"
        @feature-selected="handleFeatureSelected"
      />
    </v-main>

    <!-- Правая панель -->
    <v-navigation-drawer permanent location="right" width="350">
      <RightSidebar :feature="selectedFeature" />
    </v-navigation-drawer>
  </v-layout>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useStore } from 'vuex';
import LeftSidebar from '@/components/LeftSidebar.vue';
import MapComponent from '@/components/MapComponent.vue';
import RightSidebar from '@/components/RightSidebar.vue';

const store = useStore();

// --- State ---
const selectedFeatureId = ref<string | null>(null);
const selectedFeatureType = ref<string | null>(null);

// --- Computed Properties ---
const features = computed(() => store.getters['geodata/currentFeatures']);

const allFeatures = computed(() => [
  ...features.value.points.map(f => ({ ...f, type: 'points' })),
  ...features.value.lines.map(f => ({ ...f, type: 'lines' })),
  ...features.value.polygons.map(f => ({ ...f, type: 'polygons' })),
]);

const selectedFeature = computed(() => {
  if (!selectedFeatureId.value) return null;
  return allFeatures.value.find(f => f.id === selectedFeatureId.value) || null;
});


// --- Methods ---
const handleProjectSelected = (projectId: string) => {
  selectedFeatureId.value = null; // Сбрасываем выбор объекта при смене проекта
  store.dispatch('geodata/fetchProjectDetails', projectId);
};

const handleFeatureSelected = (feature: { id: string, type: string } | null) => {
    if (feature) {
        selectedFeatureId.value = feature.id;
        selectedFeatureType.value = feature.type;
    } else {
        selectedFeatureId.value = null;
        selectedFeatureType.value = null;
    }
};
</script>

<style scoped>
.v-main {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>
