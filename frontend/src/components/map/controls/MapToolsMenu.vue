<template>
  <div class="d-flex flex-column align-end">
    <!-- Close Tool (when active) -->
    <v-fade-transition>
      <v-btn
        v-if="activeTool"
        icon="mdi-close"
        color="error"
        class="mb-2"
        elevation="4"
        @click="emit('stop')"
        title="Stop tool"
      >
        <v-icon>mdi-close</v-icon>
      </v-btn>
    </v-fade-transition>

    <!-- Import Button -->
    <v-btn
      icon="mdi-file-import"
      color="primary"
      class="mb-2"
      @click="emit('import')"
      title="Import KML/KMZ to this project"
    ></v-btn>

    <!-- Measurement Tools -->
    <v-menu location="left">
      <template v-slot:activator="{ props }">
        <v-btn
          v-bind="props"
          icon="mdi-ruler"
          :color="measureMode || isBufferMode ? 'primary' : 'white'"
          class="mb-2"
          elevation="2"
          title="Measurement Tools"
        >
          <v-icon :color="measureMode || isBufferMode ? 'white' : 'primary'">mdi-ruler</v-icon>
        </v-btn>
      </template>
      <v-list density="compact">
        <v-list-item 
          prepend-icon="mdi-ruler" 
          title="Distance" 
          @click="emit('update:measureMode', measureMode === 'length' ? null : 'length')"
          :active="measureMode === 'length'"
          color="primary"
        ></v-list-item>
        <v-list-item 
          prepend-icon="mdi-vector-square" 
          title="Area" 
          @click="emit('update:measureMode', measureMode === 'area' ? null : 'area')"
          :active="measureMode === 'area'"
          color="primary"
        ></v-list-item>
        <v-list-item 
          prepend-icon="mdi-radius-outline" 
          title="Buffer Zone" 
          @click="emit('update:isBufferMode', !isBufferMode)"
          :active="isBufferMode"
          color="primary"
        ></v-list-item>
        <v-divider></v-divider>
        <v-list-item 
          prepend-icon="mdi-trash-can-outline" 
          title="Clear Measurements" 
          @click="emit('clear')"
          color="error"
        ></v-list-item>
      </v-list>
    </v-menu>

    <!-- Raster Value Button -->
    <v-btn
      icon="mdi-eyedropper"
      :color="isRasterValueMode ? 'primary' : 'white'"
      class="mb-2"
      elevation="2"
      @click="emit('update:isRasterValueMode', !isRasterValueMode)"
      title="Значение растра"
    >
      <v-icon :color="isRasterValueMode ? 'white' : 'primary'">mdi-eyedropper</v-icon>
    </v-btn>

    <!-- Swipe / Compare Button -->
    <v-btn
      icon="mdi-compare"
      color="white"
      class="mb-2"
      elevation="2"
      @click="emit('swipe')"
      title="Swipe Tool (Compare Layers)"
    >
      <v-icon color="primary">mdi-compare</v-icon>
    </v-btn>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  activeTool: boolean;
  measureMode: 'length' | 'area' | null;
  isBufferMode: boolean;
  isRasterValueMode: boolean;
}>();

const emit = defineEmits(['stop', 'import', 'update:measureMode', 'update:isBufferMode', 'update:isRasterValueMode', 'clear', 'swipe', 'analysis']);
</script>
