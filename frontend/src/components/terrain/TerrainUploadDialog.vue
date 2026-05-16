<template>
  <v-dialog v-model="dialog" max-width="500px">
    <template v-slot:activator="{ props }">
      <v-btn color="primary" v-bind="props" prepend-icon="mdi-upload">
        Загрузить Рельеф
      </v-btn>
    </template>
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
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="blue-darken-1" variant="text" @click="dialog = false">Отмена</v-btn>
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
import { ref } from 'vue';
import geoAbstractionService from '@/services/geo-abstraction.service';

const props = defineProps<{
  projectId: string;
}>();

const emit = defineEmits(['uploaded']);

const dialog = ref(false);
const valid = ref(false);
const loading = ref(false);
const name = ref('');
const file = ref<File | null>(null);

const upload = async () => {
  if (!file.value) return;
  
  loading.value = true;
  try {
    await geoAbstractionService.createJob(props.projectId, name.value, file.value);
    dialog.value = false;
    name.value = '';
    file.value = null;
    emit('uploaded');
  } catch (error) {
    console.error('Upload failed', error);
  } finally {
    loading.value = false;
  }
};
</script>
