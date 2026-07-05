<template>
  <v-dialog v-model="internalValue" max-width="600px" persistent>
    <v-card class="elevation-12 rounded-lg">
      <v-toolbar color="blue-grey-darken-4" dark>
        <v-icon start class="ml-3">mdi-file-chart</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          Импорт NetCDF: {{ job?.name }}
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
          <v-list-item-title class="text-caption font-weight-bold">Свойства NetCDF:</v-list-item-title>
          <v-list-item-subtitle class="text-caption">
            Разрешение сетки: {{ metadata?.width || 0 }} x {{ metadata?.height || 0 }} px <br/>
            Filename: {{ metadata?.filename || 'неизвестно' }}
          </v-list-item-subtitle>
        </v-list-item>

        <v-form ref="form" v-model="isFormValid" @submit.prevent="submit">
          <v-select
            v-model="selectedVariable"
            :items="subdatasets"
            item-title="title"
            item-value="value"
            label="Выберите переменную для импорта"
            prepend-inner-icon="mdi-variable"
            :rules="[v => !!v || 'Переменная обязательна для выбора']"
            required
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
          :disabled="!isFormValid || !selectedVariable"
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
const isFormValid = ref(false);
const error = ref('');

const selectedVariable = ref('');

const metadata = computed(() => {
  return props.job?.characteristics?.metadata || {};
});

const subdatasets = computed(() => {
  const list = metadata.value?.subdatasets || [];
  return list.map((sub: any) => ({
    title: `${sub.variableName} (${sub.description.split(': ').pop()})`,
    value: sub.variableName
  }));
});

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    error.value = '';
    selectedVariable.value = '';
  }
});

const submit = async () => {
  if (!props.job || !selectedVariable.value) return;

  loading.value = true;
  error.value = '';

  try {
    const params = {
      taskType: 'NETCDF_COG',
      netcdfVariable: selectedVariable.value
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
