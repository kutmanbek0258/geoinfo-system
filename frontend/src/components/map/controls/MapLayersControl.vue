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
        icon="mdi-layers"
        color="white"
        class="mb-2"
        elevation="2"
        title="Imagery Layers"
      >
        <v-icon color="primary">mdi-layers</v-icon>
      </v-btn>
    </template>
    
    <v-card width="280">
      <v-card-title class="d-flex align-center py-2 px-4 bg-primary text-white">
        <v-icon class="mr-2">mdi-layers</v-icon>
        <span class="text-subtitle-1 font-weight-bold">Imagery Layers</span>
      </v-card-title>
      
      <v-divider></v-divider>
      
      <v-card-text class="pa-0" style="max-height: 250px; overflow-y: auto;">
        <v-list dense class="py-1">
          <v-list-item v-if="layers.length === 0" class="text-center text-grey py-4">
            No imagery layers found
          </v-list-item>
          <v-list-item v-for="layer in layers" :key="layer.id" class="px-4 py-1">
            <v-checkbox
              :label="layer.name"
              :value="layer.id"
              :model-value="visibleIds"
              @update:model-value="val => emit('update:visibleIds', val)"
              @change="emit('toggle', layer)"
              hide-details
              density="compact"
              color="primary"
              class="w-100"
            ></v-checkbox>
            <v-slider
              v-if="visibleIds.includes(layer.id)"
              :model-value="opacities[layer.id] || 100"
              @update:model-value="val => emit('update:opacity', { id: layer.id, value: val })"
              min="0"
              max="100"
              step="1"
              hide-details
              dense
              color="primary"
              class="mt-n2 px-2"
            ></v-slider>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </v-menu>
</template>

<script setup lang="ts">
import { ref } from 'vue';

defineProps<{
  layers: any[];
  visibleIds: string[];
  opacities: Record<string, number>;
}>();

const emit = defineEmits(['update:visibleIds', 'toggle', 'update:opacity']);

const menuOpen = ref(false);
</script>
