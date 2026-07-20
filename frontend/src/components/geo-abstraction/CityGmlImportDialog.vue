<template>
  <v-dialog v-model="internalValue" max-width="600px" persistent>
    <v-card class="elevation-12 rounded-lg">
      <v-toolbar color="blue-grey-darken-4" dark>
        <v-icon start class="ml-3">mdi-office-building-map</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          Импорт CityGML в 3D Tiles: {{ job?.name }}
        </v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn icon @click="close" :disabled="loading">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-toolbar>

      <v-card-text class="pa-6">
        <v-list-item class="bg-grey-lighten-4 rounded mb-4" density="compact">
          <template v-slot:prepend>
            <v-icon color="teal">mdi-information-outline</v-icon>
          </template>
          <v-list-item-title class="text-caption font-weight-bold">Свойства CityGML:</v-list-item-title>
          <v-list-item-subtitle class="text-caption">
            Файл GML: {{ metadata?.gmlFile || 'неизвестно' }} <br/>
            Система координат (SRS): {{ metadata?.srsName || 'Не обнаружена' }} <br/>
            Зданий/Объектов: {{ metadata?.buildingCount || 0 }}
          </v-list-item-subtitle>
        </v-list-item>

        <v-form ref="form" v-model="isFormValid" @submit.prevent="submit">
          <div class="text-body-2 text-grey-darken-2 mb-3">
            Внимание: Пространственная модель CityGML (.gml / .xml) будет обработана и конвертирована в тайлсет 3D Tiles (b3dm + tileset.json) с автоматической геопривязкой на карте Cesium.
          </div>

          <!-- Texture Generation Checkbox -->
          <v-checkbox
            v-model="includeTextures"
            label="Генерировать растровые текстуры фасадов и крыш"
            color="teal-darken-1"
            density="comfortable"
            hide-details
            class="mb-3 font-weight-medium text-body-2"
          ></v-checkbox>

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
          color="teal-darken-1"
          variant="flat"
          @click="submit"
          :loading="loading"
          :disabled="!isFormValid"
        >
          Начать импорт CityGML
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
const includeTextures = ref(true);
const error = ref('');

const metadata = computed(() => {
  return props.job?.characteristics?.metadata || {};
});

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    error.value = '';
    includeTextures.value = true;
  }
});

const submit = async () => {
  if (!props.job) return;

  loading.value = true;
  error.value = '';

  try {
    const params = {
      taskType: 'CITYGML',
      includeTextures: includeTextures.value
    };

    await geoAbstractionService.startImport(props.job.id, params);
    
    internalValue.value = false;
    emit('imported');
  } catch (err: any) {
    console.error('CityGML import failed', err);
    error.value = err?.response?.data?.message || err.message || 'Ошибка запуска конвертации CityGML';
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
