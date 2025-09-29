<template>
  <v-sheet class="d-flex flex-column pa-4" height="100%">
    <!-- Project Selector -->
    <v-select
      label="Project"
      :items="projects"
      item-title="name"
      item-value="id"
      v-model="selectedProject"
      @update:modelValue="onProjectChange"
      variant="outlined"
      density="compact"
      hide-details
    ></v-select>

    <v-divider class="my-4"></v-divider>

    <!-- Geo Objects List -->
    <v-expansion-panels v-if="selectedProject" variant="accordion">
      <v-expansion-panel title="Points" :badge="features.points.length">
        <v-expansion-panel-text>
          <v-list density="compact">
            <v-list-item
              v-for="point in features.points"
              :key="point.id"
              :title="point.name"
              @click="emit('feature-selected', { id: point.id, type: 'points' })"
              link
            ></v-list-item>
          </v-list>
        </v-expansion-panel-text>
      </v-expansion-panel>
      <!-- Similar expansion panels for lines and polygons -->
    </v-expansion-panels>
    
    <v-spacer></v-spacer>

    <!-- Action Buttons -->
    <div v-if="selectedProject">
      <v-btn block class="mb-2" color="primary">Add Point</v-btn>
      <v-btn block class="mb-2">Import KML</v-btn>
    </div>
  </v-sheet>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useStore } from 'vuex';

const emit = defineEmits(['project-selected', 'feature-selected']);

const store = useStore();
const selectedProject = ref<string | null>(null);

// --- Vuex State ---
const projects = computed(() => store.getters['geodata/allProjects']);
const features = computed(() => store.getters['geodata/currentFeatures']);

// --- Lifecycle ---
onMounted(() => {
  store.dispatch('geodata/fetchProjects');
});

// --- Methods ---
const onProjectChange = (projectId: string) => {
  if (projectId) {
    emit('project-selected', projectId);
  }
};
</script>
