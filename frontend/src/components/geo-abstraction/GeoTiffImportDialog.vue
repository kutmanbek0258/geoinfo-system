<template>
  <v-dialog v-model="internalValue" max-width="600px" persistent>
    <v-card class="elevation-12 rounded-lg">
      <v-toolbar color="blue-grey-darken-4" dark>
        <v-icon start class="ml-3">mdi-file-image</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          Импорт GeoTIFF: {{ job?.name }}
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
          <v-list-item-title class="text-caption font-weight-bold">Свойства растра:</v-list-item-title>
          <v-list-item-subtitle class="text-caption">
            Размер: {{ metadata?.width }} x {{ metadata?.height }} px <br/>
            Каналов: {{ metadata?.bandsCount || 0 }} <br/>
            Filename: {{ metadata?.filename || 'неизвестно' }}
          </v-list-item-subtitle>
        </v-list-item>

        <v-form ref="form" v-model="isFormValid" @submit.prevent="submit">
          <v-select
            v-model="renderMode"
            :items="[
              { title: 'Аналитический (для числовых вычислений/DEM)', value: 'analytic' },
              { title: 'Web RGB (для визуализации/RGB ортофото)', value: 'web_rgb' }
            ]"
            item-title="title"
            item-value="value"
            label="Режим визуализации"
            variant="outlined"
            density="comfortable"
            class="mb-3"
            :disabled="loading"
          ></v-select>

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

const renderMode = ref('analytic');

const metadata = computed(() => {
  return props.job?.characteristics?.metadata || {};
});

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    error.value = '';
    renderMode.value = metadata.value?.bandsCount >= 3 ? 'web_rgb' : 'analytic';
  }
});

const submit = async () => {
  if (!props.job) return;

  loading.value = true;
  error.value = '';

  try {
    const params = {
      taskType: 'RAW_GEOTIFF_OPTIMIZE',
      renderMode: renderMode.value,
      outputMode: renderMode.value
    };

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
