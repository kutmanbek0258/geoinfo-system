<template>
  <v-container fluid>
    <v-row>
      <!-- Секция фоновых задач (Jobs) -->
      <v-col cols="12">
        <v-card elevation="2">
          <v-toolbar color="blue-grey-darken-3" dark density="comfortable">
            <v-icon start class="ml-2">mdi-cog-sync</v-icon>
            <v-toolbar-title>Задачи обработки</v-toolbar-title>
            <v-spacer></v-spacer>
            
            <v-menu>
              <template v-slot:activator="{ props }">
                <v-btn color="success" v-bind="props" prepend-icon="mdi-plus" class="mr-2">
                  Создать задачу
                </v-btn>
              </template>
              <v-list>
                <v-list-item @click="showTerrainDialog = true">
                  <template v-slot:prepend>
                    <v-icon color="brown">mdi-terrain</v-icon>
                  </template>
                  <v-list-item-title>3D Рельеф (Terrain)</v-list-item-title>
                </v-list-item>
                <v-list-item @click="showSatelliteDialog = true">
                  <template v-slot:prepend>
                    <v-icon color="primary">mdi-satellite-variant</v-icon>
                  </template>
                  <v-list-item-title>Растровые данные (GeoTIFF, Спутники)</v-list-item-title>                </v-list-item>
              </v-list>
            </v-menu>

            <TerrainUploadDialog 
              v-model="showTerrainDialog"
              :project-id="selectedProjectId" 
              @uploaded="onNewJobCreated" 
            />
            
            <SatelliteImageryUploadDialog 
              v-model="showSatelliteDialog"
              @uploaded="onNewJobCreated" 
            />

            <v-btn icon="mdi-refresh" variant="text" @click="fetchJobs" :loading="isLoadingJobs"></v-btn>
          </v-toolbar>

          <v-progress-linear :active="isLoadingJobs" indeterminate color="blue-grey"></v-progress-linear>

          <v-table hover>
            <thead>
              <tr>
                <th>Тип</th>
                <th>Название задачи</th>
                <th>Статус</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in terrainJobs" :key="job.id">
                <td>
                  <v-tooltip :text="getTaskIcon(job.taskType).label" location="top">
                    <template v-slot:activator="{ props }">
                      <v-icon v-bind="props" :color="getTaskIcon(job.taskType).color">
                        {{ getTaskIcon(job.taskType).icon }}
                      </v-icon>
                    </template>
                  </v-tooltip>
                </td>
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
                        <v-icon v-bind="props" color="error" size="small" class="ml-2">mdi-alert-circle</v-icon>
                      </template>
                    </v-tooltip>
                  </div>
                </td>
              </tr>
              <tr v-if="terrainJobs.length === 0 && !isLoadingJobs">
                <td colspan="3" class="text-center text-grey py-4">Активные задачи отсутствуют</td>
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
import type { TerrainJob } from '@/types/api';
import TerrainUploadDialog from './TerrainUploadDialog.vue';
import SatelliteImageryUploadDialog from './SatelliteImageryUploadDialog.vue';

const store = useStore();

const jobPage = ref(1);
const pageSize = ref(10);

const showTerrainDialog = ref(false);
const showSatelliteDialog = ref(false);

const isLoadingJobs = ref(false);

const terrainJobs = computed<TerrainJob[]>(() => store.state.geodata.terrainJobs?.content || []);
const totalJobPages = computed(() => store.state.geodata.terrainJobs?.totalPages || 0);

const selectedProjectId = computed(() => store.state.geodata.selectedProjectId || '');

// Polling for jobs
let pollingInterval: any = null;

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

const getJobStatusColor = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'READY': return 'success';
    case 'FAILED': return 'error';
    case 'PROCESSING': return 'info';
    case 'QUEUED': return 'grey';
    default: return 'warning';
  }
};

const getTaskIcon = (taskType: string | undefined) => {
  switch (taskType) {
    case 'SENTINEL_COG':
      return { icon: 'mdi-satellite-variant', color: 'primary', label: 'Sentinel-2' };
    case 'LANDSAT_COG':
      return { icon: 'mdi-satellite-variant', color: 'indigo', label: 'Landsat 8' };
    case 'RAW_GEOTIFF_OPTIMIZE':
      return { icon: 'mdi-file-image', color: 'teal', label: 'GeoTIFF' };
    case 'TERRAIN_MESH':
      return { icon: 'mdi-terrain', color: 'brown', label: 'Terrain' };
    default:
      return { icon: 'mdi-file-cog', color: 'grey', label: 'Task' };
  }
};

const startPolling = () => {
  if (pollingInterval) return;
  pollingInterval = setInterval(() => {
    // Only poll if there are active jobs or if we are on the first page
    const hasActiveJobs = terrainJobs.value.some(j => j.status === 'PROCESSING' || j.status === 'QUEUED');
    if (hasActiveJobs || jobPage.value === 1) {
      fetchJobs();
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
  fetchJobs();
  startPolling();
});

onUnmounted(() => {
  stopPolling();
});

watch(jobPage, () => fetchJobs());
</script>

<style scoped>
.v-table th {
  background-color: #f5f5f5;
  font-weight: bold !important;
}
</style>
