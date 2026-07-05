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
                <v-list-item @click="openUploadDialog('SENTINEL_2')">
                  <template v-slot:prepend>
                    <v-icon color="primary">mdi-satellite-variant</v-icon>
                  </template>
                  <v-list-item-title>Снимки Sentinel-2</v-list-item-title>
                </v-list-item>
                <v-list-item @click="openUploadDialog('LANDSAT_8')">
                  <template v-slot:prepend>
                    <v-icon color="indigo">mdi-satellite-variant</v-icon>
                  </template>
                  <v-list-item-title>Снимки Landsat-8</v-list-item-title>
                </v-list-item>
                <v-list-item @click="openUploadDialog('GEOTIFF')">
                  <template v-slot:prepend>
                    <v-icon color="teal">mdi-file-image</v-icon>
                  </template>
                  <v-list-item-title>Файл GeoTIFF</v-list-item-title>
                </v-list-item>
                <v-list-item @click="openUploadDialog('TERRAIN')">
                  <template v-slot:prepend>
                    <v-icon color="brown">mdi-terrain</v-icon>
                  </template>
                  <v-list-item-title>3D Рельеф (Terrain DEM)</v-list-item-title>
                </v-list-item>
                <v-list-item @click="openUploadDialog('NETCDF')">
                  <template v-slot:prepend>
                    <v-icon color="blue">mdi-file-chart</v-icon>
                  </template>
                  <v-list-item-title>Данные NetCDF</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>

            <!-- Unified Upload Dialog -->
            <GeodataUploadDialog 
              v-model="showUploadDialog"
              :dataType="uploadDataType"
              @uploaded="onNewJobCreated" 
            />

            <!-- Split Parameter Import Dialogs -->
            <SentinelImportDialog
              v-model="showSentinelImport"
              :job="selectedJob"
              @imported="fetchJobs"
            />

            <LandsatImportDialog
              v-model="showLandsatImport"
              :job="selectedJob"
              @imported="fetchJobs"
            />

            <GeoTiffImportDialog
              v-model="showGeoTiffImport"
              :job="selectedJob"
              @imported="fetchJobs"
            />

            <TerrainImportDialog
              v-model="showTerrainImport"
              :job="selectedJob"
              @imported="fetchJobs"
            />

            <NetcdfImportDialog
              v-model="showNetcdfImport"
              :job="selectedJob"
              @imported="fetchJobs"
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
                <th class="text-right">Действия</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in terrainJobs" :key="job.id">
                <td>
                  <v-tooltip :text="getTaskIcon(job.taskType, job.characteristics?.dataType).label" location="top">
                    <template v-slot:activator="{ props }">
                      <v-icon v-bind="props" :color="getTaskIcon(job.taskType, job.characteristics?.dataType).color">
                        {{ getTaskIcon(job.taskType, job.characteristics?.dataType).icon }}
                      </v-icon>
                    </template>
                  </v-tooltip>
                </td>
                <td>{{ job.name }}</td>
                <td>
                  <div class="d-flex align-center">
                    <v-chip size="small" :color="getJobStatusColor(job.status)" class="mr-2">
                      {{ getJobStatusLabel(job.status) }}
                    </v-chip>
                    <v-progress-circular 
                      v-if="job.status === 'PROCESSING' || job.status === 'QUEUED' || job.status === 'VERIFYING'"
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
                <td class="text-right">
                  <v-btn
                    v-if="job.status === 'VERIFIED'"
                    color="teal-darken-1"
                    size="small"
                    prepend-icon="mdi-import"
                    @click="openImportDialog(job)"
                    variant="flat"
                  >
                    Импортировать
                  </v-btn>
                </td>
              </tr>
              <tr v-if="terrainJobs.length === 0 && !isLoadingJobs">
                <td colspan="4" class="text-center text-grey py-4">Активные задачи отсутствуют</td>
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
import GeodataUploadDialog from './GeodataUploadDialog.vue';
import SentinelImportDialog from './SentinelImportDialog.vue';
import LandsatImportDialog from './LandsatImportDialog.vue';
import GeoTiffImportDialog from './GeoTiffImportDialog.vue';
import TerrainImportDialog from './TerrainImportDialog.vue';
import NetcdfImportDialog from './NetcdfImportDialog.vue';

const store = useStore();

const jobPage = ref(1);
const pageSize = ref(10);

const showUploadDialog = ref(false);
const uploadDataType = ref('');

const selectedJob = ref<any>(null);
const showSentinelImport = ref(false);
const showLandsatImport = ref(false);
const showGeoTiffImport = ref(false);
const showTerrainImport = ref(false);
const showNetcdfImport = ref(false);

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

const openUploadDialog = (dataType: string) => {
  uploadDataType.value = dataType;
  showUploadDialog.value = true;
};

const openImportDialog = (job: any) => {
  selectedJob.value = job;
  const dataType = job.characteristics?.dataType;
  
  if (dataType === 'SENTINEL_2') {
    showSentinelImport.value = true;
  } else if (dataType === 'LANDSAT_8') {
    showLandsatImport.value = true;
  } else if (dataType === 'GEOTIFF') {
    showGeoTiffImport.value = true;
  } else if (dataType === 'TERRAIN') {
    showTerrainImport.value = true;
  } else if (dataType === 'NETCDF') {
    showNetcdfImport.value = true;
  }
};

const getJobStatusLabel = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'READY': return 'Готов';
    case 'FAILED': return 'Ошибка';
    case 'PROCESSING': return 'Обработка...';
    case 'VERIFYING': return 'Проверка...';
    case 'VERIFIED': return 'Проверен';
    case 'QUEUED': return 'В очереди';
    default: return status || 'Новый';
  }
};

const getJobStatusColor = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'READY': return 'success';
    case 'FAILED': return 'error';
    case 'PROCESSING': return 'info';
    case 'VERIFYING': return 'amber-darken-3';
    case 'VERIFIED': return 'teal-darken-1';
    case 'QUEUED': return 'grey';
    default: return 'warning';
  }
};

const getTaskIcon = (taskType: string | undefined, dataType: string | undefined) => {
  // If verifying, we check the dataType parameter
  if (taskType === 'VERIFY_FILE' || !taskType) {
    switch (dataType) {
      case 'SENTINEL_2':
        return { icon: 'mdi-satellite-variant', color: 'amber-darken-3', label: 'Sentinel-2 (Проверка...)' };
      case 'LANDSAT_8':
        return { icon: 'mdi-satellite-variant', color: 'amber-darken-3', label: 'Landsat 8 (Проверка...)' };
      case 'GEOTIFF':
        return { icon: 'mdi-file-image', color: 'amber-darken-3', label: 'GeoTIFF (Проверка...)' };
      case 'TERRAIN':
        return { icon: 'mdi-terrain', color: 'amber-darken-3', label: 'Terrain (Проверка...)' };
      case 'NETCDF':
        return { icon: 'mdi-file-chart', color: 'amber-darken-3', label: 'NetCDF (Проверка...)' };
      default:
        return { icon: 'mdi-file-check-outline', color: 'amber-darken-3', label: 'Верификация...' };
    }
  }

  switch (taskType) {
    case 'SENTINEL_COG':
      return { icon: 'mdi-satellite-variant', color: 'primary', label: 'Sentinel-2' };
    case 'LANDSAT_COG':
      return { icon: 'mdi-satellite-variant', color: 'indigo', label: 'Landsat 8' };
    case 'RAW_GEOTIFF_OPTIMIZE':
      return { icon: 'mdi-file-image', color: 'teal', label: 'GeoTIFF' };
    case 'TERRAIN_MESH':
      return { icon: 'mdi-terrain', color: 'brown', label: 'Terrain' };
    case 'NETCDF_COG':
      return { icon: 'mdi-file-chart', color: 'blue', label: 'NetCDF' };
    default:
      return { icon: 'mdi-file-cog', color: 'grey', label: 'Task' };
  }
};

const startPolling = () => {
  if (pollingInterval) return;
  pollingInterval = setInterval(() => {
    // Only poll if there are active jobs or if we are on the first page
    const hasActiveJobs = terrainJobs.value.some(j => j.status === 'PROCESSING' || j.status === 'QUEUED' || j.status === 'VERIFYING');
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
