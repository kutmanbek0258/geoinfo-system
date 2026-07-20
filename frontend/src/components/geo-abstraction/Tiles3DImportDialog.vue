<template>
  <v-dialog v-model="internalValue" max-width="600px" persistent>
    <v-card class="elevation-12 rounded-lg">
      <v-toolbar color="blue-grey-darken-4" dark>
        <v-icon start class="ml-3">mdi-cube-outline</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          Генерация 3D Tiles: {{ job?.name }}
        </v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn icon @click="close" :disabled="loading">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-toolbar>

      <v-card-text class="pa-6">
        <v-list-item class="bg-grey-lighten-4 rounded mb-4" density="compact">
          <template v-slot:prepend>
            <v-icon color="indigo">mdi-information-outline</v-icon>
          </template>
          <v-list-item-title class="text-caption font-weight-bold">Свойства 3D модели:</v-list-item-title>
          <v-list-item-subtitle class="text-caption">
            Формат: {{ metadata?.format || 'OBJ' }} <br/>
            Исходный файл OBJ: {{ metadata?.containedObjFile || 'неизвестно' }}
          </v-list-item-subtitle>
        </v-list-item>

        <v-form ref="form" v-model="isFormValid" @submit.prevent="submit">
          <div class="text-body-2 text-grey-darken-2 mb-3">
            Внимание: Модель 3D объекта (.obj / .zip) будет обработана с помощью инструмента py3dtiles для создания тайлсета 3D Tiles (tileset.json и b3dm/pnts тайлов) для 3D карты Cesium.
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
          color="indigo-darken-1"
          variant="flat"
          @click="submit"
          :loading="loading"
          :disabled="!isFormValid"
        >
          Начать генерацию 3D Tiles
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
      taskType: '3D_TILES'
    };

    await geoAbstractionService.startImport(props.job.id, params);
    
    internalValue.value = false;
    emit('imported');
  } catch (err: any) {
    console.error('3D Tiles generation failed', err);
    error.value = err?.response?.data?.message || err.message || 'Ошибка запуска генерации 3D Tiles';
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
