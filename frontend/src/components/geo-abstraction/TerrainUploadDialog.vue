<template>
  <v-dialog v-model="internalValue" max-width="500px">
    <v-card>
      <v-card-title>
        <span class="text-h5">Загрузка GeoTIFF рельефа</span>
      </v-card-title>
      <v-card-text>
        <v-form ref="form" v-model="valid">
          <v-text-field
            v-model="name"
            label="Название слоя"
            :rules="[v => !!v || 'Название обязательно']"
            required
          ></v-text-field>
          <v-file-input
            v-model="file"
            label="Выберите GeoTIFF файл"
            accept=".tif,.tiff"
            :rules="[v => !!v || 'Файл обязателен']"
            required
            prepend-icon="mdi-file-image"
          ></v-file-input>

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
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="blue-darken-1" variant="text" @click="internalValue = false" :disabled="loading">Отмена</v-btn>
        <v-btn
          color="blue-darken-1"
          variant="text"
          @click="upload"
          :loading="loading"
          :disabled="!valid || !file"
        >
          Загрузить
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import geoAbstractionService from '@/services/geo-abstraction.service';

const props = defineProps<{
  projectId: string;
  modelValue: boolean;
}>();

const emit = defineEmits(['uploaded', 'update:modelValue']);

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
    await geoAbstractionService.confirmJob(
      name.value,
      objectKey,
      file.value.size,
      'TERRAIN_MESH'
    );
    
    internalValue.value = false;
    name.value = '';
    file.value = null;
    emit('uploaded');
  } catch (error) {
    console.error('Upload failed', error);
  } finally {
    loading.value = false;
    uploadProgress.value = 0;
  }
};
</script>
