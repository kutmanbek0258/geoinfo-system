<template>
  <v-dialog v-model="modelValue" max-width="500px">
    <v-card>
      <v-card-title>New {{ drawingType }}</v-card-title>
      <v-card-text>
        <v-text-field v-model="metadata.name" label="Name" required></v-text-field>
        <v-textarea v-model="metadata.description" label="Description"></v-textarea>
        <v-select v-model="metadata.status" :items="['COMPLETED', 'IN_PROCESS', 'REJECTED']" label="Status" required></v-select>
        
        <!-- Point Type Selection (only for Point drawing) -->
        <v-select
          v-if="drawingType === 'Point'"
          v-model="metadata.type"
          :items="pointTypes"
          label="Point Type"
          required
        ></v-select>

        <!-- Camera Details (conditional) -->
        <template v-if="drawingType === 'Point' && metadata.type === 'camera'">
          <v-text-field v-model="cameraDetails.ip_address" label="Camera IP Address" required></v-text-field>
          <v-text-field v-model="cameraDetails.port" label="Camera Port" type="number" required></v-text-field>
          <v-text-field v-model="cameraDetails.login" label="Camera Login" required></v-text-field>
          <v-text-field v-model="cameraDetails.password" label="Camera Password" type="password" required></v-text-field>
        </template>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="cancel">Cancel</v-btn>
        <v-btn color="primary" @click="save">Save</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  modelValue: boolean;
  drawingType: string;
  metadata: any;
  cameraDetails: any;
  pointTypes: string[];
}>();

const emit = defineEmits(['update:modelValue', 'cancel', 'save']);

const modelValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const cancel = () => {
  emit('cancel');
};

const save = () => {
  emit('save');
};
</script>
