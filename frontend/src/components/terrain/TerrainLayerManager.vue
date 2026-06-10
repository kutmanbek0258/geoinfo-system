<template>
  <v-card elevation="2">
    <v-toolbar color="brown" dark density="comfortable">
      <v-icon start class="ml-2">mdi-layers-triple</v-icon>
      <v-toolbar-title>Слои рельефа (Terrain Layers)</v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn icon="mdi-refresh" variant="text" @click="fetchLayers" :loading="isLoading"></v-btn>
    </v-toolbar>

    <v-progress-linear :active="isLoading" indeterminate color="brown"></v-progress-linear>

    <v-table hover>
      <thead>
        <tr>
          <th>Название</th>
          <th>Статус</th>
          <th class="text-right">Действия</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="layer in terrainLayers" :key="layer.id">
          <td>{{ layer.title }}</td>
          <td>
            <v-chip size="small" :color="getStatusColor(layer.status)">
              {{ layer.status }}
            </v-chip>
          </td>
          <td class="text-right">
            <v-btn icon="mdi-delete" size="small" variant="text" color="error" @click="deleteLayer(layer.id)"></v-btn>
          </td>
        </tr>
        <tr v-if="terrainLayers.length === 0 && !isLoading">
          <td colspan="3" class="text-center text-grey py-4">Слои не найдены</td>
        </tr>
      </tbody>
    </v-table>

    <v-divider></v-divider>
    <div class="pa-2 d-flex justify-center">
      <v-pagination
        v-model="page"
        :length="totalPages"
        density="comfortable"
        total-visible="5"
      ></v-pagination>
    </div>
  </v-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useStore } from 'vuex';
import type { TerrainLayer } from '@/types/api';

const store = useStore();

const page = ref(1);
const pageSize = ref(10);

const isLoading = computed(() => store.state.geodata.isLoading);
const terrainLayers = computed<TerrainLayer[]>(() => store.state.geodata.terrainLayers?.content || []);
const totalPages = computed(() => store.state.geodata.terrainLayers?.totalPages || 0);

const fetchLayers = () => {
  store.dispatch('geodata/fetchTerrainLayers', { page: page.value - 1, size: pageSize.value });
};

const deleteLayer = async (id: string) => {
  if (confirm('Вы уверены, что хотите удалить этот слой?')) {
    await store.dispatch('geodata/deleteTerrainLayer', { 
      layerId: id, 
      page: page.value - 1, 
      size: pageSize.value 
    });
  }
};

const getStatusColor = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'READY': return 'success';
    case 'ERROR': return 'error';
    default: return 'warning';
  }
};

onMounted(() => {
  fetchLayers();
});

watch(page, () => fetchLayers());

// Expose fetchLayers so parent can call it (e.g. after a job finishes)
defineExpose({ fetchLayers });
</script>

<style scoped>
.v-table th {
  background-color: #f5f5f5;
  font-weight: bold !important;
}
</style>
