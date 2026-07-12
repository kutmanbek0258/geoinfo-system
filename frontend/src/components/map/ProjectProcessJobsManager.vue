<template>
  <v-card class="project-jobs-manager pa-2">
    <v-card-title class="d-flex justify-between align-center bg-blue-grey-darken-3 text-white py-3 px-4 rounded-t">
      <div class="d-flex align-center">
        <v-icon start class="mr-2">mdi-import</v-icon>
        <span class="text-h6 font-weight-bold">Импорт растровых слоев и рельефа</span>
      </div>
      <v-spacer></v-spacer>
      <v-btn icon="mdi-close" variant="text" color="white" @click="$emit('close')"></v-btn>
    </v-card-title>

    <v-card-text class="pt-4">
      <v-row>
        <v-col cols="12">
          <div class="d-flex justify-space-between align-center mb-4">
            <span class="text-subtitle-1 font-weight-medium">Список активных и завершенных задач обработки</span>
            
            <div class="d-flex align-center">
              <v-menu>
                <template v-slot:activator="{ props }">
                  <v-btn color="success" v-bind="props" prepend-icon="mdi-plus" class="mr-3">
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
                  <v-list-item @click="openUploadDialog('NETCDF')">
                    <template v-slot:prepend>
                      <v-icon color="blue">mdi-file-chart</v-icon>
                    </template>
                    <v-list-item-title>Данные NetCDF</v-list-item-title>
                  </v-list-item>
                </v-list>
              </v-menu>
              <v-btn icon="mdi-refresh" variant="outlined" density="comfortable" color="primary" @click="fetchJobs" :loading="isLoadingJobs"></v-btn>
            </div>
          </div>

          <!-- Unified Upload Dialog -->
          <GeodataUploadDialog 
            v-model="showUploadDialog"
            :dataType="uploadDataType"
            @uploaded="onNewJobCreated" 
          />

          <!-- Import Dialogs -->
          <SentinelImportDialog
            v-model="showSentinelImport"
            :job="selectedJob"
            @imported="onImported"
          />
          <LandsatImportDialog
            v-model="showLandsatImport"
            :job="selectedJob"
            @imported="onImported"
          />
          <GeoTiffImportDialog
            v-model="showGeoTiffImport"
            :job="selectedJob"
            @imported="onImported"
          />
          <NetcdfImportDialog
            v-model="showNetcdfImport"
            :job="selectedJob"
            @imported="onImported"
          />

          <v-progress-linear :active="isLoadingJobs" indeterminate color="primary" height="2"></v-progress-linear>

          <v-table hover class="border rounded">
            <thead>
              <tr class="bg-grey-lighten-4">
                <th class="font-weight-bold">Тип</th>
                <th class="font-weight-bold">Название задачи</th>
                <th class="font-weight-bold">Статус</th>
                <th class="font-weight-bold text-right">Действия</th>
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
                      v-if="['PROCESSING', 'QUEUED', 'VERIFYING'].includes(job.status)"
                      indeterminate 
                      size="16" 
                      width="2" 
                      color="primary"
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
                  <span v-else class="text-caption text-grey">Ожидание...</span>
                </td>
              </tr>
              <tr v-if="terrainJobs.length === 0 && !isLoadingJobs">
                <td colspan="4" class="text-center text-grey py-6">Активные задачи отсутствуют. Нажмите кнопку "Создать задачу" выше.</td>
              </tr>
            </tbody>
          </v-table>

          <v-divider class="my-4"></v-divider>
          <div class="d-flex justify-center">
            <v-pagination
              v-model="jobPage"
              :length="totalJobPages"
              density="comfortable"
              total-visible="5"
            ></v-pagination>
          </div>
        </v-col>
      </v-row>
    </v-card-text>

    <v-divider></v-divider>
    <v-card-actions class="pa-4">
      <v-spacer></v-spacer>
      <v-btn color="grey-darken-1" variant="text" @click="$emit('close')">Закрыть</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { TerrainJob } from '@/types/api';
import GeodataUploadDialog from '../geo-abstraction/GeodataUploadDialog.vue';
import SentinelImportDialog from '../geo-abstraction/SentinelImportDialog.vue';
import LandsatImportDialog from '../geo-abstraction/LandsatImportDialog.vue';
import GeoTiffImportDialog from '../geo-abstraction/GeoTiffImportDialog.vue';
import TerrainImportDialog from '../geo-abstraction/TerrainImportDialog.vue';
import NetcdfImportDialog from '../geo-abstraction/NetcdfImportDialog.vue';

const store = useStore();
const emit = defineEmits(['close']);

const jobPage = ref(1);
const pageSize = ref(10);

const showUploadDialog = ref(false);
const uploadDataType = ref('');

const selectedJob = ref<any>(null);
const showSentinelImport = ref(false);
const showLandsatImport = ref(false);
const showGeoTiffImport = ref(false);
const showNetcdfImport = ref(false);

const isLoadingJobs = ref(false);

const terrainJobs = computed<TerrainJob[]>(() => store.state.geodata.terrainJobs?.content || []);
const totalJobPages = computed(() => store.state.geodata.terrainJobs?.totalPages || 0);

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
  jobPage.value = 1;
  fetchJobs();
  startPolling();
};

const onImported = () => {
  fetchJobs();
  // Fetch newly imported imagery/terrain layers to refresh the map immediately
  store.dispatch('geodata/fetchProjectRasters');
  store.dispatch('geodata/fetchTerrainLayers');
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
  if (taskType === 'VERIFY_FILE' || !taskType) {
    switch (dataType) {
      case 'SENTINEL_2':
        return { icon: 'mdi-satellite-variant', color: 'amber-darken-3', label: 'Sentinel-2 (Проверка...)' };
      case 'LANDSAT_8':
        return { icon: 'mdi-satellite-variant', color: 'amber-darken-3', label: 'Landsat 8 (Проверка...)' };
      case 'GEOTIFF':
        return { icon: 'mdi-file-image', color: 'amber-darken-3', label: 'GeoTIFF (Проверка...)' };
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
    case 'NETCDF_COG':
      return { icon: 'mdi-file-chart', color: 'blue', label: 'NetCDF' };
    default:
      return { icon: 'mdi-file-cog', color: 'grey', label: 'Task' };
  }
};

const startPolling = () => {
  if (pollingInterval) return;
  pollingInterval = setInterval(() => {
    const hasActiveJobs = terrainJobs.value.some(j => ['PROCESSING', 'QUEUED', 'VERIFYING'].includes(j.status));
    if (hasActiveJobs || jobPage.value === 1) {
      fetchJobs();
    }
  }, 5000);
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
.project-jobs-manager {
  max-height: 90vh;
  overflow-y: auto;
}
.v-table th {
  background-color: #f5f5f5;
  font-weight: bold !important;
}
</style>
