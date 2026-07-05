<template>
  <v-dialog v-model="internalValue" max-width="600px" persistent>
    <v-card class="elevation-12 rounded-lg">
      <v-toolbar color="blue-grey-darken-4" dark>
        <v-icon start class="ml-3">mdi-satellite-variant</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          Импорт снимка Sentinel-2: {{ job?.name }}
        </v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn icon @click="close" :disabled="loading">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-toolbar>

      <v-card-text class="pa-6">
        <v-list-item class="bg-grey-lighten-4 rounded mb-4" density="compact">
          <template v-slot:prepend>
            <v-icon color="blue-grey">mdi-information-outline</v-icon>
          </template>
          <v-list-item-title class="text-caption font-weight-bold">Извлеченные метаданные:</v-list-item-title>
          <v-list-item-subtitle class="text-caption">
            Проекция: {{ metadata?.crs ? 'Задана' : 'Не определена' }} <br/>
            Найденные каналы: {{ metadata?.availableBands?.join(', ') || 'нет' }}
          </v-list-item-subtitle>
        </v-list-item>

        <v-form ref="form" v-model="isFormValid" @submit.prevent="submit">
          <v-select
            v-model="importMode"
            :items="[
              { title: 'Цветовой синтез (RGB Пресет)', value: 'preset' },
              { title: 'Вегетационный/Водный индекс', value: 'index' },
              { title: 'Пользовательский синтез каналов', value: 'custom' }
            ]"
            item-title="title"
            item-value="value"
            label="Режим импорта"
            variant="outlined"
            density="comfortable"
            class="mb-3"
            :disabled="loading"
          ></v-select>

          <v-select
            v-if="importMode === 'preset'"
            v-model="selectedPreset"
            :items="presetsList"
            label="Спектральный пресет"
            variant="outlined"
            density="comfortable"
            class="mb-3"
            :disabled="loading"
          ></v-select>

          <v-select
            v-if="importMode === 'index'"
            v-model="selectedIndex"
            :items="indicesList"
            label="Рассчитываемый индекс"
            variant="outlined"
            density="comfortable"
            class="mb-3"
            :disabled="loading"
          ></v-select>

          <div v-if="importMode === 'custom'">
            <div class="text-subtitle-2 mb-2 text-blue-grey-darken-2 font-weight-bold">
              Выберите спектральные каналы:
            </div>
            <v-row>
              <v-col cols="4">
                <v-select
                  v-model="customChannels[0]"
                  :items="availableBands"
                  label="Красный (R)"
                  variant="outlined"
                  density="comfortable"
                  :disabled="loading"
                ></v-select>
              </v-col>
              <v-col cols="4">
                <v-select
                  v-model="customChannels[1]"
                  :items="availableBands"
                  label="Зеленый (G)"
                  variant="outlined"
                  density="comfortable"
                  :disabled="loading"
                ></v-select>
              </v-col>
              <v-col cols="4">
                <v-select
                  v-model="customChannels[2]"
                  :items="availableBands"
                  label="Синий (B)"
                  variant="outlined"
                  density="comfortable"
                  :disabled="loading"
                ></v-select>
              </v-col>
            </v-row>
          </div>

          <v-alert
            v-if="error"
            type="error"
            title="Ошибка запуска"
            :text="error"
            closable
            class="mb-4"
            density="compact"
          ></v-alert>
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
          color="primary"
          variant="flat"
          @click="submit"
          :loading="loading"
          :disabled="!isFormValid"
        >
          Запустить импорт
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import geoAbstractionService from '@/services/geo-abstraction.service';

const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true
  },
  job: {
    type: Object,
    default: null
  }
});

const emit = defineEmits(['update:modelValue', 'imported']);

const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const loading = ref(false);
const isFormValid = ref(true);
const error = ref('');

const importMode = ref('preset');
const selectedPreset = ref('Natural Color (B04, B03, B02)');
const selectedIndex = ref('NDVI');
const customChannels = ref<string[]>(['B04', 'B03', 'B02']);

const metadata = computed(() => {
  return props.job?.characteristics?.metadata || {};
});

const availableBands = computed(() => {
  return metadata.value?.availableBands || ['B01', 'B02', 'B03', 'B04', 'B05', 'B06', 'B07', 'B08', 'B8A', 'B09', 'B10', 'B11', 'B12'];
});

const presetsList = [
  'Natural Color (B04, B03, B02)',
  'False Color Infrared (B08, B04, B03)',
  'Agriculture (B11, B08, B04)',
  'Shortwave Infrared (B12, B08, B04)',
  'Healthy Vegetation (B08, B11, B02)'
];

const indicesList = [
  'NDVI',
  'NDWI',
  'NDMI',
  'NBR',
  'NDSI',
  'NDBI',
  'SAVI',
  'EVI',
  'GNDVI'
];

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    error.value = '';
    importMode.value = 'preset';
    selectedPreset.value = 'Natural Color (B04, B03, B02)';
    selectedIndex.value = 'NDVI';
    customChannels.value = ['B04', 'B03', 'B02'];
  }
});

const getChannelsForPreset = (preset: string): string[] => {
  if (preset.includes('Natural Color')) return ['B04', 'B03', 'B02'];
  if (preset.includes('False Color Infrared')) return ['B08', 'B04', 'B03'];
  if (preset.includes('Agriculture')) return ['B11', 'B08', 'B04'];
  if (preset.includes('Shortwave Infrared')) return ['B12', 'B08', 'B04'];
  if (preset.includes('Healthy Vegetation')) return ['B08', 'B11', 'B02'];
  return ['B04', 'B03', 'B02'];
};

const submit = async () => {
  if (!props.job) return;

  loading.value = true;
  error.value = '';

  try {
    let channels: string[] = [];
    let indexType: string | undefined = undefined;

    if (importMode.value === 'preset') {
      channels = getChannelsForPreset(selectedPreset.value);
    } else if (importMode.value === 'index') {
      indexType = selectedIndex.value;
    } else {
      channels = [...customChannels.value];
    }

    const params: Record<string, any> = {
      taskType: 'SENTINEL_COG',
      channels,
    };
    if (indexType) {
      params.indexType = indexType;
    }

    await geoAbstractionService.startImport(props.job.id, params);
    
    internalValue.value = false;
    emit('imported');
  } catch (err: any) {
    console.error('Import failed', err);
    error.value = err?.response?.data?.message || err.message || 'Ошибка запуска импорта';
  } finally {
    loading.value = false;
  }
};

const close = () => {
  if (!loading.value) {
    internalValue.value = false;
  }
};
</script>
