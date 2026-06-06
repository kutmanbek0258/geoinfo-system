<template>
  <v-menu
    v-model="menuOpen"
    :close-on-content-click="false"
    location="bottom end"
    offset="5"
    transition="slide-y-transition"
  >
    <template v-slot:activator="{ props }">
      <v-btn
        v-bind="props"
        icon="mdi-terrain"
        color="white"
        class="mb-2"
        elevation="2"
        title="Terrain Layers"
      >
        <v-icon color="primary">mdi-terrain</v-icon>
      </v-btn>
    </template>
    
    <v-card width="280">
      <v-card-title class="d-flex align-center py-2 px-4 bg-primary text-white">
        <v-icon class="mr-2">mdi-terrain</v-icon>
        <span class="text-subtitle-1 font-weight-bold">Terrain Layers</span>
      </v-card-title>
      
      <v-divider></v-divider>
      
      <v-card-text class="pa-0" style="max-height: 200px; overflow-y: auto;">
        <v-radio-group
          :model-value="modelValue"
          @update:model-value="val => emit('update:modelValue', val)"
          hide-details
          class="px-4 py-2"
        >
          <v-radio label="World Terrain" :value="null" color="primary" density="compact" class="mb-1"></v-radio>
          <v-radio
            v-for="layer in layers"
            :key="layer.id"
            :label="layer.title"
            :value="layer.id"
            color="primary"
            density="compact"
            class="mb-1"
          ></v-radio>
        </v-radio-group>
      </v-card-text>
    </v-card>
  </v-menu>
</template>

<script setup lang="ts">
import { ref } from 'vue';

defineProps<{
  modelValue: string | null;
  layers: any[];
}>();

const emit = defineEmits(['update:modelValue']);
const menuOpen = ref(false);
</script>
