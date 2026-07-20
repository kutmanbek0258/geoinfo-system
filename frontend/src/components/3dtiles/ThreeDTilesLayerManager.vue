<template>
  <v-card elevation="2">
    <v-toolbar color="deep-purple" dark density="comfortable">
      <v-icon start class="ml-2">mdi-cube-outline</v-icon>
      <v-toolbar-title>Слои 3D Tiles (3D Tiles Layers)</v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn icon="mdi-refresh" variant="text" @click="fetchLayers" :loading="isLoading"></v-btn>
    </v-toolbar>

    <v-progress-linear :active="isLoading" indeterminate color="deep-purple"></v-progress-linear>

    <v-table hover>
      <thead>
        <tr>
          <th>Название 3D-слоя</th>
          <th>URL тайлсета</th>
          <th>Статус</th>
          <th class="text-right">Действия</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="layer in threeDTilesLayers" :key="layer.id">
          <td class="font-weight-medium">{{ layer.title }}</td>
          <td class="text-caption text-grey-darken-1">{{ layer.tilesetUrl }}</td>
          <td>
            <v-chip size="small" :color="getStatusColor(layer.status)">
              {{ layer.status }}
            </v-chip>
          </td>
          <td class="text-right">
            <v-btn icon="mdi-delete" size="small" variant="text" color="error" title="Удалить слой" @click="deleteLayer(layer.id)"></v-btn>
          </td>
        </tr>
        <tr v-if="threeDTilesLayers.length === 0 && !isLoading">
          <td colspan="4" class="text-center text-grey py-6">Слои 3D Tiles не найдены</td>
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
import type { ThreeDTilesLayer } from '@/types/api';

const store = useStore();

const page = ref(1);
const pageSize = ref(10);

const isLoading = computed(() => store.state.geodata.isLoading);
const threeDTilesLayers = computed<ThreeDTilesLayer[]>(() => store.state.geodata.threeDTilesLayers?.content || []);
const totalPages = computed(() => store.state.geodata.threeDTilesLayers?.totalPages || 0);

const fetchLayers = () => {
  store.dispatch('geodata/fetch3DTilesLayers', { page: page.value - 1, size: pageSize.value });
};

const deleteLayer = async (id: string) => {
  if (confirm('Вы уверены, что хотите удалить этот 3D Tiles слой?')) {
    await store.dispatch('geodata/delete3DTilesLayer', { 
      layerId: id, 
      page: page.value - 1, 
      size: pageSize.value 
    });
  }
};

const getStatusColor = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'READY': return 'success';
    case 'ERROR':
    case 'FAILED': return 'error';
    default: return 'warning';
  }
};

onMounted(() => {
  fetchLayers();
});

watch(page, () => fetchLayers());

defineExpose({ fetchLayers });
</script>

<style scoped>
.v-table th {
  background-color: #f5f5f5;
  font-weight: bold !important;
}
</style>
