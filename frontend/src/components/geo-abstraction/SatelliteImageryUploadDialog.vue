<template>
  <v-dialog v-model="internalValue" max-width="700px">
    <v-card>
      <v-card-title class="d-flex align-center">
        <v-icon start>mdi-satellite-variant</v-icon>
        <span class="text-h5">Обработка спутниковых снимков</span>
      </v-card-title>
      
      <v-tabs v-model="satelliteType" color="primary" grow>
        <v-tab value="sentinel">Sentinel-2</v-tab>
        <v-tab value="landsat">Landsat 8</v-tab>
        <v-tab value="geotiff">GeoTIFF</v-tab>
      </v-tabs>

      <v-card-text class="pt-4">
        <v-form ref="form" v-model="valid">
          <v-text-field
            v-model="name"
            label="Название задачи"
            placeholder="Напр. My_GeoTIFF_Area"
            :rules="[v => !!v || 'Название обязательно']"
            required
            density="comfortable"
          ></v-text-field>

          <v-file-input
            v-model="file"
            :label="getFileLabel"
            :accept="getFileAccept"
            :rules="[v => !!v || 'Файл обязателен']"
            required
            :prepend-icon="satelliteType === 'geotiff' ? 'mdi-file-image' : 'mdi-zip-box'"
            :hint="getFileHint"
            persistent-hint
            density="comfortable"
          ></v-file-input>

          <v-expand-transition>
            <div v-if="satelliteType !== 'geotiff'">
              <v-divider class="my-4"></v-divider>

              <v-select
                v-model="selectedPreset"
                :items="currentPresets"
                label="Выберите пресет или индекс"
                prepend-icon="mdi-tune-variant"
                @update:modelValue="onPresetChange"
                density="comfortable"
              ></v-select>

              <v-expand-transition>
                <div v-if="selectedPreset === 'Custom'">
                  <v-select
                    v-model="selectedChannels"
                    :items="availableChannels"
                    label="Выберите спектральные каналы вручную"
                    multiple
                    chips
                    hint="Каналы будут объединены в один COG-файл"
                    persistent-hint
                    :rules="[v => v.length > 0 || 'Выберите хотя бы один канал']"
                    class="mt-2"
                    density="comfortable"
                  ></v-select>
                </div>
                <div v-else-if="selectedPreset !== 'Custom'">
                  <v-alert
                    type="info"
                    variant="tonal"
                    density="compact"
                    class="mt-2"
                  >
                    Используемые каналы: {{ getPresetChannels(selectedPreset).join(', ') }}
                  </v-alert>
                </div>
              </v-expand-transition>
            </div>
          </v-expand-transition>

          <v-expand-transition>
            <div v-if="loading" class="mt-4">
              <div class="d-flex justify-space-between mb-1">
                <span class="text-caption">{{ uploadStatusText }}</span>
                <span class="text-caption">{{ uploadProgress }}%</span>
              </div>
              <v-progress-linear
                v-model="uploadProgress"
                color="primary"
                height="8"
                rounded
                indeterminate
                v-if="uploadProgress === 0"
              ></v-progress-linear>
              <v-progress-linear
                v-model="uploadProgress"
                color="primary"
                height="8"
                rounded
                v-else
              ></v-progress-linear>
            </div>
          </v-expand-transition>

        </v-form>
      </v-card-text>
      
      <v-divider></v-divider>
      
      <v-card-actions class="pa-4">
        <v-spacer></v-spacer>
        <v-btn color="grey-darken-1" variant="text" @click="internalValue = false" :disabled="loading">Отмена</v-btn>
        <v-btn
          color="success"
          variant="elevated"
          @click="upload"
          :loading="loading"
          :disabled="!valid || !file || (satelliteType !== 'geotiff' && selectedPreset === 'Custom' && selectedChannels.length === 0)"
        >
          Запустить обработку
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import geoAbstractionService from '@/services/geo-abstraction.service';

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits(['uploaded', 'update:modelValue']);

const store = useStore();

const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const valid = ref(false);
const loading = ref(false);
const uploadProgress = ref(0);
const uploadStatusText = ref('');
const name = ref('');
const file = ref<File | null>(null);
const satelliteType = ref<'sentinel' | 'landsat' | 'geotiff'>('sentinel');

const getFileLabel = computed(() => {
  if (satelliteType.value === 'sentinel') return 'Выберите Sentinel-2 SAFE (ZIP)';
  if (satelliteType.value === 'landsat') return 'Выберите Landsat 8 (ZIP/TAR)';
  return 'Выберите GeoTIFF (.tif, .tiff)';
});

const getFileAccept = computed(() => {
  if (satelliteType.value === 'sentinel') return '.zip';
  if (satelliteType.value === 'landsat') return '.zip,.tar,.gz,.tgz';
  return '.tif,.tiff';
});

const getFileHint = computed(() => {
  if (satelliteType.value === 'sentinel') return 'Загрузите весь пакет .SAFE в виде ZIP-архива';
  if (satelliteType.value === 'landsat') return 'Загрузите архив с каналами Landsat 8';
  return 'Загрузите файл GeoTIFF для оптимизации и публикации';
});

const sentinelChannels = [
  { title: 'B02 (Blue, 10m)', value: 'B02' },
  { title: 'B03 (Green, 10m)', value: 'B03' },
  { title: 'B04 (Red, 10m)', value: 'B04' },
  { title: 'B08 (NIR, 10m)', value: 'B08' },
  { title: 'B05 (Red Edge, 20m)', value: 'B05' },
  { title: 'B06 (Red Edge, 20m)', value: 'B06' },
  { title: 'B07 (Red Edge, 20m)', value: 'B07' },
  { title: 'B11 (SWIR, 20m)', value: 'B11' },
  { title: 'B12 (SWIR, 20m)', value: 'B12' },
  { title: 'B8A (Narrow NIR, 20m)', value: 'B8A' },
];

const landsatChannels = [
  { title: 'B1 (Coastal/Aerosol, 30m)', value: 'B1' },
  { title: 'B2 (Blue, 30m)', value: 'B2' },
  { title: 'B3 (Green, 30m)', value: 'B3' },
  { title: 'B4 (Red, 30m)', value: 'B4' },
  { title: 'B5 (NIR, 30m)', value: 'B5' },
  { title: 'B6 (SWIR 1, 30m)', value: 'B6' },
  { title: 'B7 (SWIR 2, 30m)', value: 'B7' },
  { title: 'B8 (Panchromatic, 15m)', value: 'B8' },
  { title: 'B9 (Cirrus, 30m)', value: 'B9' },
  { title: 'B10 (TIRS 1, 100m)', value: 'B10' },
  { title: 'B11 (TIRS 2, 100m)', value: 'B11' },
];

const sentinelPresets = {
  'Natural Color (4-3-2)': ['B04', 'B03', 'B02'],
  'False Color Vegetation (8-4-3)': ['B08', 'B04', 'B03'],
  'Red Edge Agro (8A-6-4)': ['B8A', 'B06', 'B04'],
  'Moisture / Burn (12-11-4)': ['B12', 'B11', 'B04'],
  'Urban / Soil (12-11-8)': ['B12', 'B11', 'B08'],
  'Water emphasis (8-3-2)': ['B08', 'B03', 'B02'],
};

const sentinelIndices = {
  'NDVI (Vegetation Index)': ['B08', 'B04'],
  'NDWI (Water Index)': ['B03', 'B08'],
  'NDMI (Moisture Index)': ['B08', 'B11'],
  'NBR (Burn Index)': ['B08', 'B12'],
  'NDSI (Snow Index)': ['B03', 'B11'],
  'NDBI (Built-up Index)': ['B11', 'B08'],
  'SAVI (Soil Adjusted Index)': ['B08', 'B04'],
  'EVI (Enhanced Vegetation)': ['B08', 'B04', 'B02'],
  'GNDVI (Chlorophyll Index)': ['B08', 'B03'],
};

const landsatPresets = {
  'Natural Color (4-3-2)': ['B4', 'B3', 'B2'],
  'False Color Vegetation (5-4-3)': ['B5', 'B4', 'B3'],
  'Agriculture / Veg Health (6-5-2)': ['B6', 'B5', 'B2'],
  'Moisture / Burn (7-6-4)': ['B7', 'B6', 'B4'],
  'Geology / Soil (7-5-3)': ['B7', 'B5', 'B3'],
  'Urban Analysis (7-6-2)': ['B7', 'B6', 'B2'],
  'Coastal / Water (1-2-3)': ['B1', 'B2', 'B3'],
};

const landsatIndices = {
  'NDVI (Vegetation Index)': ['B5', 'B4'],
  'EVI (Enhanced Vegetation)': ['B5', 'B4', 'B2'],
  'SAVI (Soil Adjusted Index)': ['B5', 'B4'],
  'MSAVI (Modified SAVI)': ['B5', 'B4'],
  'NDMI (Moisture Index)': ['B5', 'B6'],
  'NBR (Burn Index)': ['B5', 'B7'],
  'NBR2 (Post-fire Moisture)': ['B6', 'B7'],
  'NDSI (Snow Index)': ['B3', 'B6'],
  'NDWI (Water Index)': ['B3', 'B5'],
};

const currentPresets = computed(() => {
  const ps = satelliteType.value === 'sentinel' ? sentinelPresets : landsatPresets;
  const ind = satelliteType.value === 'sentinel' ? sentinelIndices : landsatIndices;
  
  return [
    { type: 'subheader', title: 'Комбинации каналов (RGB)' },
    ...Object.keys(ps).map(k => ({ title: k, value: k })),
    { type: 'divider' },
    { type: 'subheader', title: 'Спектральные индексы (Math)' },
    ...Object.keys(ind).map(k => ({ title: k, value: k })),
    { type: 'divider' },
    { title: 'Пользовательский выбор', value: 'Custom' }
  ];
});

const availableChannels = computed(() => satelliteType.value === 'sentinel' ? sentinelChannels : landsatChannels);

const selectedPreset = ref('Natural Color (4-3-2)');
const selectedChannels = ref(['B04', 'B03', 'B02']);

watch(satelliteType, (newType) => {
  if (newType === 'geotiff') return;
  selectedPreset.value = 'Natural Color (4-3-2)';
  selectedChannels.value = newType === 'sentinel' ? ['B04', 'B03', 'B02'] : ['B4', 'B3', 'B2'];
});

const onPresetChange = (preset: string) => {
  if (preset === 'Custom') return;
  selectedChannels.value = getPresetChannels(preset);
};

const getPresetChannels = (preset: string): string[] => {
  const ps = satelliteType.value === 'sentinel' ? { ...sentinelPresets, ...sentinelIndices } : { ...landsatPresets, ...landsatIndices };
  return ps[preset as keyof typeof ps] || [];
};

const getIndexType = (preset: string): string | undefined => {
  const indexPrefixes = ['NDVI', 'NDWI', 'NDMI', 'NBR', 'NDSI', 'NDBI', 'SAVI', 'EVI', 'GNDVI', 'MSAVI', 'NBR2'];
  for (const prefix of indexPrefixes) {
    if (preset.startsWith(prefix)) return prefix;
  }
  return undefined;
};

const upload = async () => {
  if (!file.value) return;
  
  loading.value = true;
  uploadProgress.value = 0;
  
  try {
    // 1. Get Presigned URL
    uploadStatusText.value = 'Подготовка к загрузке...';
    const { url, objectKey } = await geoAbstractionService.getPresignedUrl(file.value.name);
    
    // 2. Upload directly to MinIO
    uploadStatusText.value = 'Загрузка файла...';
    await geoAbstractionService.uploadFileDirectly(url, file.value, (percent) => {
      uploadProgress.value = percent;
    });
    
    // 3. Confirm job creation
    uploadStatusText.value = 'Запуск задачи...';
    const taskType = satelliteType.value === 'geotiff' ? 'RAW_GEOTIFF_OPTIMIZE' : 
                     (satelliteType.value === 'sentinel' ? 'SENTINEL_COG' : 'LANDSAT_COG');
    
    const indexType = satelliteType.value !== 'geotiff' ? getIndexType(selectedPreset.value) : undefined;
    
    await geoAbstractionService.confirmJob(
      name.value,
      objectKey,
      file.value.size,
      taskType,
      satelliteType.value !== 'geotiff' ? selectedChannels.value : undefined,
      indexType,
      store.state.geodata.selectedProjectId || undefined
    );
    
    internalValue.value = false;
    resetForm();
    emit('uploaded');
  } catch (error) {
    console.error('Upload failed', error);
  } finally {
    loading.value = false;
    uploadProgress.value = 0;
  }
};

const resetForm = () => {
  name.value = '';
  file.value = null;
  if (satelliteType.value !== 'geotiff') {
    selectedPreset.value = 'Natural Color (4-3-2)';
    selectedChannels.value = satelliteType.value === 'sentinel' ? ['B04', 'B03', 'B02'] : ['B4', 'B3', 'B2'];
  }
};
</script>
