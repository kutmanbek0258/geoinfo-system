<template>
  <v-dialog v-model="internalValue" max-width="600px" persistent>
    <v-card class="elevation-12 rounded-lg">
      <v-toolbar color="blue-grey-darken-4" dark>
        <v-icon start class="ml-3">mdi-cloud-upload</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          Загрузка геоданных: {{ dataTypeLabel }}
        </v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn icon @click="close" :disabled="loading">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-toolbar>

      <v-card-text class="pa-6">
        <v-form ref="form" v-model="isFormValid" @submit.prevent="upload">
          <v-text-field
            v-model="name"
            label="Название задачи / слоя"
            prepend-inner-icon="mdi-format-title"
            :rules="[v => !!v || 'Название обязательно для заполнения']"
            required
            :disabled="loading"
            class="mb-3"
            variant="outlined"
            density="comfortable"
          ></v-text-field>

          <v-select
            v-if="!props.dataType"
            v-model="selectedDataType"
            :items="dataTypes"
            item-title="title"
            item-value="value"
            label="Тип геоданных"
            prepend-inner-icon="mdi-layers-outline"
            :rules="[v => !!v || 'Выберите тип данных']"
            required
            :disabled="loading"
            class="mb-3"
            variant="outlined"
            density="comfortable"
          ></v-select>

          <v-file-input
            v-model="file"
            :label="fileInputLabel"
            prepend-inner-icon="mdi-file-cabinet"
            :rules="fileRules"
            required
            show-size
            :disabled="loading"
            class="mb-4"
            variant="outlined"
            density="comfortable"
            @update:model-value="onFileSelected"
          ></v-file-input>

          <v-alert
            v-if="uploadError"
            type="error"
            title="Ошибка загрузки"
            :text="uploadError"
            closable
            class="mb-4"
            density="compact"
          ></v-alert>

          <div v-if="loading" class="text-center my-4">
            <div class="text-caption font-weight-bold text-blue-grey-darken-2 mb-2">
              {{ uploadStatusText }} ({{ uploadProgress }}%)
            </div>
            <v-progress-linear
              v-model="uploadProgress"
              color="teal"
              height="8"
              striped
              rounded
            ></v-progress-linear>
          </div>
        </v-form>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions class="px-6 py-4">
        <v-spacer></v-spacer>
        <v-btn
          color="grey-darken-1"
          variant="text"
          @click="close"
          :disabled="loading"
        >
          Отмена
        </v-btn>
        <v-btn
          color="teal-darken-1"
          variant="flat"
          @click="upload"
          :loading="loading"
          :disabled="!isFormValid || !file"
        >
          Загрузить и проверить
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import geoAbstractionService from '@/services/geo-abstraction.service';

const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true
  },
  dataType: {
    type: String,
    default: '' // Can be SENTINEL_2, LANDSAT_8, GEOTIFF, TERRAIN, NETCDF
  }
});

const emit = defineEmits(['update:modelValue', 'uploaded']);
const store = useStore();

const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const name = ref('');
const file = ref<File | null>(null);
const selectedDataType = ref('');
const loading = ref(false);
const isFormValid = ref(false);
const uploadProgress = ref(0);
const uploadStatusText = ref('');
const uploadError = ref('');

const dataTypes = [
  { title: 'Снимки Sentinel-2', value: 'SENTINEL_2' },
  { title: 'Снимки Landsat-8', value: 'LANDSAT_8' },
  { title: 'Файл GeoTIFF', value: 'GEOTIFF' },
  { title: '3D Рельеф (Terrain DEM)', value: 'TERRAIN' },
  { title: 'Данные NetCDF', value: 'NETCDF' },
  { title: '3D Модель (3D Tiles OBJ)', value: '3D_TILES' },
  { title: '3D Городская модель CityGML (.gml, .xml)', value: 'CITYGML' }
];

const currentDataType = computed(() => {
  return props.dataType || selectedDataType.value;
});

const dataTypeLabel = computed(() => {
  const matched = dataTypes.find(t => t.value === currentDataType.value);
  return matched ? matched.title : 'Выбор типа';
});

const fileInputLabel = computed(() => {
  switch (currentDataType.value) {
    case 'SENTINEL_2':
    case 'LANDSAT_8':
      return 'Выберите архив со снимками (.zip, .tar, .tar.gz)';
    case 'GEOTIFF':
    case 'TERRAIN':
      return 'Выберите растр GeoTIFF (.tif, .tiff) или ZIP-архив';
    case 'NETCDF':
      return 'Выберите файл NetCDF (.nc, .nc4) или ZIP-архив';
    case '3D_TILES':
      return 'Выберите 3D-модель (.obj) или ZIP-архив (.zip)';
    case 'CITYGML':
      return 'Выберите файл CityGML (.gml, .xml) или ZIP-архив (.zip)';
    default:
      return 'Выберите файл';
  }
});

const fileRules = computed(() => {
  return [
    (v: any) => {
      let fileObj = null;
      if (Array.isArray(v)) {
        fileObj = v[0] || null;
      } else {
        fileObj = v;
      }
      return !!fileObj || 'Файл обязателен для загрузки';
    },
    (v: any) => {
      let fileObj = null;
      if (Array.isArray(v)) {
        fileObj = v[0] || null;
      } else {
        fileObj = v;
      }
      if (!fileObj || !fileObj.name) return true;
      const ext = fileObj.name.substring(fileObj.name.lastIndexOf('.')).toLowerCase();
      
      switch (currentDataType.value) {
        case 'SENTINEL_2':
        case 'LANDSAT_8':
          return ['.zip', '.tar', '.gz', '.tgz'].includes(ext) || 'Неверный формат архива (разрешены .zip, .tar, .tar.gz)';
        case 'GEOTIFF':
        case 'TERRAIN':
          return ['.tif', '.tiff', '.zip'].includes(ext) || 'Разрешены только файлы .tif, .tiff или .zip';
        case 'NETCDF':
          return ['.nc', '.nc4', '.zip'].includes(ext) || 'Разрешены только файлы .nc, .nc4 или .zip';
        case '3D_TILES':
          return ['.obj', '.zip'].includes(ext) || 'Разрешены только файлы .obj или .zip';
        case 'CITYGML':
          return ['.gml', '.xml', '.zip'].includes(ext) || 'Разрешены только файлы .gml, .xml или .zip';
        default:
          return true;
      }
    }
  ];
});

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    resetForm();
    if (props.dataType) {
      selectedDataType.value = props.dataType;
    }
  }
});

const onFileSelected = (selectedFile: File | File[] | null) => {
  let fileObj: File | null = null;
  if (Array.isArray(selectedFile)) {
    fileObj = selectedFile[0] || null;
  } else {
    fileObj = selectedFile;
  }

  if (fileObj && !name.value) {
    // Generate default name from file name (strip extension)
    const fileName = fileObj.name;
    const dotIdx = fileName.lastIndexOf('.');
    name.value = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
  }
};

const upload = async () => {
  let fileObj: File | null = null;
  if (Array.isArray(file.value)) {
    fileObj = file.value[0] || null;
  } else {
    fileObj = file.value;
  }

  if (!fileObj || !isFormValid.value) return;

  loading.value = true;
  uploadProgress.value = 0;
  uploadError.value = '';

  try {
    // 1. Get Presigned URL
    uploadStatusText.value = 'Подготовка к загрузке...';
    const { url, objectKey } = await geoAbstractionService.getPresignedUrl(fileObj.name);

    // 2. Upload directly to MinIO
    uploadStatusText.value = 'Загрузка файла...';
    await geoAbstractionService.uploadFileDirectly(url, fileObj, (percent) => {
      uploadProgress.value = percent;
    });

    // 3. Initiate verification step
    uploadStatusText.value = 'Запуск верификации файла...';
    const projectId = store.state.geodata.selectedProjectId || undefined;
    
    await geoAbstractionService.verifyUpload(
      name.value,
      objectKey,
      fileObj.size,
      currentDataType.value,
      projectId
    );

    internalValue.value = false;
    resetForm();
    emit('uploaded');
  } catch (error: any) {
    console.error('Upload failed', error);
    uploadError.value = error?.response?.data?.message || error.message || 'Неизвестная ошибка при загрузке';
  } finally {
    loading.value = false;
    uploadProgress.value = 0;
  }
};

const close = () => {
  if (!loading.value) {
    internalValue.value = false;
  }
};

const resetForm = () => {
  name.value = '';
  file.value = null;
  selectedDataType.value = props.dataType || '';
  uploadError.value = '';
  uploadProgress.value = 0;
};
</script>
