<template>
  <v-dialog v-model="modelValue" max-width="500px">
    <v-card>
      <v-card-title>Import KML/KMZ to Project</v-card-title>
      <v-card-text>
        <v-file-input
          v-model="internalFile"
          label="Select KML or KMZ File"
          accept=".kml,.kmz"
          prepend-icon="mdi-file-xml"
          show-size
        ></v-file-input>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="close">Cancel</v-btn>
        <v-btn color="primary" @click="execute" :loading="loading">Import</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  modelValue: boolean;
  file: File | null;
  loading: boolean;
}>();

const emit = defineEmits(['update:modelValue', 'update:file', 'execute']);

const modelValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const internalFile = computed({
  get: () => props.file,
  set: (val) => emit('update:file', val)
});

const close = () => {
  modelValue.value = false;
};

const execute = () => {
  emit('execute');
};
</script>
