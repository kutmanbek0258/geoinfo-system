<template>
  <v-dialog v-model="dialog" max-width="600px">
    <template v-slot:activator="{ props }">
      <v-btn color="secondary" v-bind="props" prepend-icon="mdi-satellite-variant" class="ml-2">
        Загрузить Sentinel-2
      </v-btn>
    </template>
    <v-card>
      <v-card-title>
        <span class="text-h5">Обработка Sentinel-2 (.SAFE)</span>
      </v-card-title>
      <v-card-text>
        <v-form ref="form" v-model="valid">
          <v-text-field
            v-model="name"
            label="Название задачи"
            placeholder="Напр. Sentinel_Bishkek_2026"
            :rules="[v => !!v || 'Название обязательно']"
            required
          ></v-text-field>
          <v-file-input
            v-model="file"
            label="Выберите Sentinel-2 SAFE (ZIP архив)"
            accept=".zip"
            :rules="[v => !!v || 'Файл обязателен']"
            required
            prepend-icon="mdi-zip-box"
            hint="Загрузите весь пакет .SAFE в виде ZIP-архива"
            persistent-hint
          ></v-file-input>

          <v-select
            v-model="selectedChannels"
            :items="availableChannels"
            label="Выберите спектральные каналы"
            multiple
            chips
            hint="Каналы будут объединены в один COG-файл"
            persistent-hint
            :rules="[v => v.length > 0 || 'Выберите хотя бы один канал']"
            class="mt-4"
          ></v-select>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="blue-darken-1" variant="text" @click="dialog = false">Отмена</v-btn>
        <v-btn
          color="success"
          variant="elevated"
          @click="upload"
          :loading="loading"
          :disabled="!valid || !file || selectedChannels.length === 0"
        >
          Запустить обработку
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import geoAbstractionService from '@/services/geo-abstraction.service';

const emit = defineEmits(['uploaded']);

const dialog = ref(false);
const valid = ref(false);
const loading = ref(false);
const name = ref('');
const file = ref<File | null>(null);

const availableChannels = [
  { title: 'B02 (Blue, 10m)', value: 'B02' },
  { title: 'B03 (Green, 10m)', value: 'B03' },
  { title: 'B04 (Red, 10m)', value: 'B04' },
  { title: 'B08 (NIR, 10m)', value: 'B08' },
  { title: 'B05 (Red Edge, 20m)', value: 'B05' },
  { title: 'B06 (Red Edge, 20m)', value: 'B06' },
  { title: 'B07 (Red Edge, 20m)', value: 'B07' },
  { title: 'B11 (SWIR, 20m)', value: 'B11' },
  { title: 'B12 (SWIR, 20m)', value: 'B12' },
];

const selectedChannels = ref(['B04', 'B03', 'B02']); // Default to RGB

const upload = async () => {
  if (!file.value) return;
  
  loading.value = true;
  try {
    await geoAbstractionService.createSentinelJob(name.value, file.value, selectedChannels.value);
    dialog.value = false;
    name.value = '';
    file.value = null;
    selectedChannels.value = ['B04', 'B03', 'B02'];
    emit('uploaded');
  } catch (error) {
    console.error('Sentinel Upload failed', error);
  } finally {
    loading.value = false;
  }
};
</script>
