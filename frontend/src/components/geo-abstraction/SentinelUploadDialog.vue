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
            v-model="selectedPreset"
            :items="allPresets"
            label="Выберите пресет или индекс"
            prepend-icon="mdi-tune-variant"
            @update:modelValue="onPresetChange"
            class="mt-4"
          ></v-select>

          <v-select
            v-if="selectedPreset === 'Custom'"
            v-model="selectedChannels"
            :items="availableChannels"
            label="Выберите спектральные каналы вручную"
            multiple
            chips
            hint="Каналы будут объединены в один COG-файл"
            persistent-hint
            :rules="[v => v.length > 0 || 'Выберите хотя бы один канал']"
            class="mt-4"
          ></v-select>
          
          <v-alert
            v-else-if="selectedPreset !== 'Custom'"
            type="info"
            variant="tonal"
            class="mt-4"
          >
            Используемые каналы: {{ getPresetChannels(selectedPreset).join(', ') }}
          </v-alert>

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
          :disabled="!valid || !file || (selectedPreset === 'Custom' && selectedChannels.length === 0)"
        >
          Запустить обработку
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
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
  { title: 'B8A (Narrow NIR, 20m)', value: 'B8A' },
];

const presets = {
  'Natural Color (4-3-2)': ['B04', 'B03', 'B02'],
  'False Color Vegetation (8-4-3)': ['B08', 'B04', 'B03'],
  'Red Edge Agro (8A-6-4)': ['B8A', 'B06', 'B04'],
  'Moisture / Burn (12-11-4)': ['B12', 'B11', 'B04'],
  'Urban / Soil (12-11-8)': ['B12', 'B11', 'B08'],
  'Water emphasis (8-3-2)': ['B08', 'B03', 'B02'],
};

const indices = {
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

const allPresets = [
  { type: 'subheader', title: 'Комбинации каналов (RGB)' },
  ...Object.keys(presets).map(k => ({ title: k, value: k })),
  { type: 'divider' },
  { type: 'subheader', title: 'Спектральные индексы (Math)' },
  ...Object.keys(indices).map(k => ({ title: k, value: k })),
  { type: 'divider' },
  { title: 'Пользовательский выбор', value: 'Custom' }
];

const selectedPreset = ref('Natural Color (4-3-2)');
const selectedChannels = ref(['B04', 'B03', 'B02']);

const onPresetChange = (preset: string) => {
  if (preset === 'Custom') {
    return;
  }
  selectedChannels.value = getPresetChannels(preset);
};

const getPresetChannels = (preset: string): string[] => {
  if (presets[preset as keyof typeof presets]) return presets[preset as keyof typeof presets];
  if (indices[preset as keyof typeof indices]) return indices[preset as keyof typeof indices];
  return [];
};

const getIndexType = (preset: string): string | undefined => {
  if (preset.startsWith('NDVI')) return 'NDVI';
  if (preset.startsWith('NDWI')) return 'NDWI';
  if (preset.startsWith('NDMI')) return 'NDMI';
  if (preset.startsWith('NBR')) return 'NBR';
  if (preset.startsWith('NDSI')) return 'NDSI';
  if (preset.startsWith('NDBI')) return 'NDBI';
  if (preset.startsWith('SAVI')) return 'SAVI';
  if (preset.startsWith('EVI')) return 'EVI';
  if (preset.startsWith('GNDVI')) return 'GNDVI';
  return undefined;
};

const upload = async () => {
  if (!file.value) return;
  
  loading.value = true;
  try {
    const indexType = getIndexType(selectedPreset.value);
    await geoAbstractionService.createSentinelJob(
        name.value, 
        file.value, 
        selectedChannels.value,
        indexType
    );
    dialog.value = false;
    name.value = '';
    file.value = null;
    selectedPreset.value = 'Natural Color (4-3-2)';
    selectedChannels.value = ['B04', 'B03', 'B02'];
    emit('uploaded');
  } catch (error) {
    console.error('Sentinel Upload failed', error);
  } finally {
    loading.value = false;
  }
};
</script>
