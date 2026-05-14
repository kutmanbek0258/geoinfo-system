<template>
  <v-container fluid>
    <v-row>
      <!-- Секция слоев рельефа -->
      <v-col cols="12" md="6">
        <v-card elevation="2">
          <v-toolbar color="brown" dark density="comfortable">
            <v-icon start class="ml-2">mdi-layers-triple</v-icon>
            <v-toolbar-title>Terrain Layers</v-toolbar-title>
            <v-spacer></v-spacer>
            <TerrainUploadDialog :project-id="selectedProjectId" @uploaded="onNewJobCreated" />
          </v-toolbar>

          <v-progress-linear :active="isLoadingLayers" indeterminate color="brown"></v-progress-linear>

          <v-table hover>
            <thead>
              <tr>
                <th>Title</th>
                <th>Status</th>
                <th class="text-right">Actions</th>
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
              <tr v-if="terrainLayers.length === 0 && !isLoadingLayers">
                <td colspan="3" class="text-center text-grey py-4">No layers found</td>
              </tr>
            </tbody>
          </v-table>

          <v-divider></v-divider>
          <div class="pa-2 d-flex justify-center">
            <v-pagination
              v-model="layerPage"
              :length="totalLayerPages"
              density="comfortable"
              total-visible="5"
            ></v-pagination>
          </div>
        </v-card>
      </v-col>

      <!-- Секция фоновых задач (Jobs) -->
      <v-col cols="12" md="6">
        <v-card elevation="2">
          <v-toolbar color="blue-grey-darken-3" dark density="comfortable">
            <v-icon start class="ml-2">mdi-cog-sync</v-icon>
            <v-toolbar-title>Processing Tasks</v-toolbar-title>
            <v-spacer></v-spacer>
            <v-btn icon="mdi-refresh" variant="text" @click="fetchJobs" :loading="isLoadingJobs"></v-btn>
          </v-toolbar>

          <v-progress-linear :active="isLoadingJobs" indeterminate color="blue-grey"></v-progress-linear>

          <v-table hover>
            <thead>
              <tr>
                <th>Task Name</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in terrainJobs" :key="job.id">
                <td>{{ job.name }}</td>
                <td>
                  <div class="d-flex align-center">
                    <v-chip size="small" :color="getJobStatusColor(job.status)" class="mr-2">
                      {{ job.status }}
                    </v-chip>
                    <v-progress-circular 
                      v-if="job.status === 'PROCESSING' || job.status === 'QUEUED'"
                      indeterminate 
                      size="16" 
                      width="2" 
                      color="blue-grey"
                    ></v-progress-circular>
                    <v-tooltip v-if="job.errorMessage" :text="job.errorMessage" location="top">
                      <template v-slot:activator="{ props }">
                        <v-icon v-bind="props" color="error" size="small">mdi-alert-circle</v-icon>
                      </template>
                    </v-tooltip>
                  </div>
                </td>
              </tr>
              <tr v-if="terrainJobs.length === 0 && !isLoadingJobs">
                <td colspan="2" class="text-center text-grey py-4">No active tasks</td>
              </tr>
            </tbody>
          </v-table>

          <v-divider></v-divider>
          <div class="pa-2 d-flex justify-center">
            <v-pagination
              v-model="jobPage"
              :length="totalJobPages"
              density="comfortable"
              total-visible="5"
            ></v-pagination>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { TerrainLayer, TerrainJob } from '@/types/api';
import TerrainUploadDialog from './TerrainUploadDialog.vue';

const store = useStore();

const layerPage = ref(1);
const jobPage = ref(1);
const pageSize = ref(10);

const isLoadingLayers = computed(() => store.state.geodata.isLoading);
const isLoadingJobs = ref(false);

const terrainLayers = computed<TerrainLayer[]>(() => store.state.geodata.terrainLayers?.content || []);
const totalLayerPages = computed(() => store.state.geodata.terrainLayers?.totalPages || 0);

const terrainJobs = computed<TerrainJob[]>(() => store.state.geodata.terrainJobs?.content || []);
const totalJobPages = computed(() => store.state.geodata.terrainJobs?.totalPages || 0);

const selectedProjectId = computed(() => store.state.geodata.selectedProjectId || '');

// Polling for jobs
let pollingInterval: any = null;

const fetchLayers = () => {
  store.dispatch('geodata/fetchTerrainLayers', { page: layerPage.value - 1, size: pageSize.value });
};

const fetchJobs = async () => {
  isLoadingJobs.value = true;
  try {
    await store.dispatch('geodata/fetchTerrainJobs', { page: jobPage.value - 1, size: pageSize.value });
  } finally {
    isLoadingJobs.value = false;
  }
};

const onNewJobCreated = () => {
  jobPage.value = 1; // Go to first page to see new job
  fetchJobs();
  // If no polling yet, start it
  if (!pollingInterval) {
    startPolling();
  }
};

const getStatusColor = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'READY': return 'success';
    case 'ERROR': return 'error';
    default: return 'warning';
  }
};

const getJobStatusColor = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'READY': return 'success';
    case 'FAILED': return 'error';
    case 'PROCESSING': return 'info';
    case 'QUEUED': return 'grey';
    default: return 'warning';
  }
};

const deleteLayer = async (id: string) => {
  if (confirm('Are you sure you want to delete this terrain layer?')) {
    await store.dispatch('geodata/deleteTerrainLayer', { 
      layerId: id, 
      page: layerPage.value - 1, 
      size: pageSize.value 
    });
  }
};

const startPolling = () => {
  if (pollingInterval) return;
  pollingInterval = setInterval(() => {
    // Only poll if there are active jobs or if we are on the first page
    const hasActiveJobs = terrainJobs.value.some(j => j.status === 'PROCESSING' || j.status === 'QUEUED');
    if (hasActiveJobs || jobPage.value === 1) {
      fetchJobs();
      // Also refresh layers if something might have finished
      if (hasActiveJobs) fetchLayers();
    }
  }, 5000); // Every 5 seconds
};

const stopPolling = () => {
  if (pollingInterval) {
    clearInterval(pollingInterval);
    pollingInterval = null;
  }
};

onMounted(() => {
  fetchLayers();
  fetchJobs();
  startPolling();
});

onUnmounted(() => {
  stopPolling();
});

watch(layerPage, () => fetchLayers());
watch(jobPage, () => fetchJobs());
</script>

<style scoped>
.v-table th {
  background-color: #f5f5f5;
  font-weight: bold !important;
}
</style>
