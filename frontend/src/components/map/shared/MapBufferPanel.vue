<template>
  <v-fade-transition>
    <div v-if="active" class="map-overlay buffer-panel">
      <div class="d-flex align-center justify-space-between mb-2">
        <span class="text-subtitle-2 font-weight-bold">Buffer Zone {{ is3D ? '(3D)' : '' }}</span>
        <v-btn icon="mdi-close" size="x-small" variant="text" @click="close"></v-btn>
      </div>
      
      <div v-if="!hasCenter" class="text-caption text-grey mb-2">
        {{ is3D ? 'Click on map to set center' : 'Click on a map object to create a buffer' }}
      </div>
      
      <template v-else>
        <div class="text-caption mb-1">Center: [{{ center[0].toFixed(4) }}, {{ center[1].toFixed(4) }}]</div>
        <v-slider
          :model-value="distance"
          @update:model-value="val => emit('update:distance', val)"
          min="1"
          max="10000"
          step="1"
          hide-details
          thumb-label
          color="primary"
          label="Radius (m)"
          class="mb-2"
        ></v-slider>
        <v-text-field
          :model-value="distance"
          @update:model-value="val => emit('update:distance', Number(val))"
          type="number"
          density="compact"
          hide-details
          suffix="meters"
          variant="outlined"
          class="mb-1"
        ></v-text-field>
        <div v-if="!is3D" class="text-caption text-grey text-center mt-1">
          Drag mouse to change radius
        </div>
      </template>
    </div>
  </v-fade-transition>
</template>

<script setup lang="ts">
const props = defineProps<{
  active: boolean;
  is3D?: boolean;
  hasCenter: boolean;
  center: number[];
  distance: number;
}>();

const emit = defineEmits(['update:active', 'update:distance']);

const close = () => {
  emit('update:active', false);
};
</script>

<style scoped>
.map-overlay {
  position: absolute;
  background-color: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  padding: 8px;
  z-index: 1000;
}

.buffer-panel {
  bottom: 20px;
  right: 60px;
  width: 220px;
}
</style>
