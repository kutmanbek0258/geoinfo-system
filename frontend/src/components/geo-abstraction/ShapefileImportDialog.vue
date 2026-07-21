<template>
  <v-dialog v-model="internalValue" max-width="600px" persistent>
    <v-card class="elevation-12 rounded-lg">
      <v-toolbar color="blue-grey-darken-4" dark>
        <v-icon start class="ml-3" color="deep-orange">mdi-shape-polygon-plus</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          Импорт Shapefile: {{ job?.name }}
        </v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn icon @click="close" :disabled="loading">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-toolbar>

      <v-card-text class="pa-6">
        <v-list-item class="bg-grey-lighten-4 rounded mb-4" density="compact">
          <template v-slot:prepend>
            <v-icon color="deep-orange">mdi-information-outline</v-icon>
          </template>
          <v-list-item-title class="text-caption font-weight-bold">Результаты верификации пакета:</v-list-item-title>
          <v-list-item-subtitle class="text-caption">
            Всего объектов: <strong>{{ metadata?.totalFeatures || 0 }}</strong> <br/>
            Слои в файле: {{ metadata?.layerNames?.join(', ') || 'основной слой' }} <br/>
            Типы геометрий: {{ metadata?.geomTypes?.join(', ') || 'Векторные геометрии' }} <br/>
            Формат: {{ metadata?.format || 'SHAPEFILE_ZIP' }}
          </v-list-item-subtitle>
        </v-list-item>

        <v-form ref="form" v-model="isFormValid" @submit.prevent="submit">
          <v-alert
            type="info"
            variant="tonal"
            density="compact"
            class="mb-4"
          >
            Все геометрии будут автоматически трансформированы в систему координат <strong>WGS 84 (EPSG:4326)</strong>, а атрибуты DBF импортированы в слой проекта.
          </v-alert>

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
          color="deep-orange-darken-1"
          variant="flat"
          @click="submit"
          :loading="loading"
          :disabled="!isFormValid"
        >
          Подтвердить и импортировать
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

const metadata = computed(() => {
  return props.job?.characteristics?.metadata || {};
});

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    error.value = '';
  }
});

const submit = async () => {
  if (!props.job) return;

  loading.value = true;
  error.value = '';

  try {
    const params = {
      taskType: 'SHAPEFILE_TO_GEOJSON'
    };

    await geoAbstractionService.startImport(props.job.id, params);
    
    internalValue.value = false;
    emit('imported');
  } catch (err: any) {
    console.error('Shapefile import failed', err);
    error.value = err?.response?.data?.message || err.message || 'Ошибка запуска импорта Shapefile';
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
