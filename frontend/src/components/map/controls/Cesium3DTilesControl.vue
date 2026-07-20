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
        icon="mdi-cube-outline"
        color="white"
        class="mb-2"
        elevation="2"
        title="3D Tiles Слои"
      >
        <v-icon color="deep-purple">mdi-cube-outline</v-icon>
      </v-btn>
    </template>
    
    <v-card width="320">
      <v-card-title class="d-flex align-center py-2 px-4 bg-deep-purple text-white">
        <v-icon class="mr-2">mdi-cube-outline</v-icon>
        <span class="text-subtitle-1 font-weight-bold">3D Tiles Слои</span>
      </v-card-title>
      
      <v-divider></v-divider>
      
      <v-card-text class="pa-2" style="max-height: 250px; overflow-y: auto;">
        <div v-if="!layers || layers.length === 0" class="text-caption text-grey text-center py-4">
          Нет доступных 3D Tiles слоев
        </div>
        
        <v-list v-else density="compact" class="py-0">
          <v-list-item
            v-for="layer in layers"
            :key="layer.id"
            class="px-2"
          >
            <template v-slot:prepend>
              <v-checkbox-btn
                :model-value="visibleIds.includes(layer.id)"
                @update:model-value="() => toggleLayer(layer.id)"
                color="deep-purple"
              ></v-checkbox-btn>
            </template>

            <v-list-item-title class="text-body-2 font-weight-medium">
              {{ layer.title }}
            </v-list-item-title>

            <template v-slot:append>
              <v-btn
                icon="mdi-magnify-scan"
                variant="text"
                size="small"
                color="deep-purple"
                title="Приблизить к 3D модели"
                :disabled="!visibleIds.includes(layer.id)"
                @click="emit('zoom', layer.id)"
              >
                <v-icon size="18">mdi-magnify-scan</v-icon>
              </v-btn>
            </template>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </v-menu>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { ThreeDTilesLayer } from '@/types/api';

const props = defineProps<{
  layers: ThreeDTilesLayer[];
  visibleIds: string[];
}>();

const emit = defineEmits(['update:visibleIds', 'zoom']);
const menuOpen = ref(false);

const toggleLayer = (id: string) => {
  const current = [...props.visibleIds];
  const idx = current.indexOf(id);
  if (idx >= 0) {
    current.splice(idx, 1);
  } else {
    current.push(id);
  }
  emit('update:visibleIds', current);
};
</script>
