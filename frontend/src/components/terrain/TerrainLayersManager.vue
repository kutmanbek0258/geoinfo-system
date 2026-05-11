<template>
  <v-card>
    <v-toolbar color="brown" dark>
      <v-toolbar-title>Terrain Layers</v-toolbar-title>
      <v-spacer></v-spacer>
      <TerrainUploadDialog :project-id="selectedProjectId" @uploaded="fetchCurrentPage" />
    </v-toolbar>

    <v-progress-linear :active="isLoading" indeterminate color="brown"></v-progress-linear>

    <v-list lines="two">
      <v-list-item
        v-for="layer in terrainLayers"
        :key="layer.id"
      >
        <v-list-item-title>{{ layer.title }}</v-list-item-title>
        <v-list-item-subtitle>
          Status: {{ layer.status }}
        </v-list-item-subtitle>

        <template v-slot:append>
          <v-btn icon="mdi-delete" variant="text" color="error" @click="deleteLayer(layer.id)"></v-btn>
        </template>
      </v-list-item>
    </v-list>

    <div class="text-center">
        <v-pagination
            v-model="currentPage"
            :length="totalPages"
            rounded="circle"
        ></v-pagination>
    </div>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { TerrainLayer } from '@/types/api';
import TerrainUploadDialog from './TerrainUploadDialog.vue';

const store = useStore();

const currentPage = ref(1);
const pageSize = ref(10);

const isLoading = computed(() => store.state.geodata.isLoading);
const terrainLayers = computed<TerrainLayer[]>(() => store.state.geodata.terrainLayers?.content || []);
const totalPages = computed(() => store.state.geodata.terrainLayers?.totalPages || 0);
const selectedProjectId = computed(() => store.state.geodata.selectedProjectId || '');

const fetchCurrentPage = () => {
    store.dispatch('geodata/fetchTerrainLayers', { page: currentPage.value - 1, size: pageSize.value });
}

onMounted(() => {
  fetchCurrentPage();
});

watch(currentPage, () => {
    fetchCurrentPage();
});

const deleteLayer = async (id: string) => {
  if (confirm('Are you sure you want to delete this terrain layer?')) {
    const actionPayload = { 
        layerId: id, 
        page: currentPage.value - 1, 
        size: pageSize.value 
    };
    await store.dispatch('geodata/deleteTerrainLayer', actionPayload);
  }
};
</script>

<style scoped>
.headline {
  font-weight: 500;
}
</style>
