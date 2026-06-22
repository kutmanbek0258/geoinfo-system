<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits(['select-tool']);

const menuOpen = ref(false);

const tools = [
  { 
    name: 'terrain_contours', 
    title: 'Изолинии рельефа', 
    icon: 'mdi-chart-bell-curve',
    description: 'Генерация контуров высот из DEM' 
  },
  { 
    name: 'slope', 
    title: 'Крутизна уклонов', 
    icon: 'mdi-slope-uphill',
    description: 'Расчет углов наклона поверхности' 
  },
  { 
    name: 'aspect', 
    title: 'Направление экспозиции', 
    icon: 'mdi-compass-outline',
    description: 'Определение ориентации уклонов по сторонам света' 
  },
  { 
    name: 'hillshade', 
    title: 'Теневая отмывка', 
    icon: 'mdi-brightness-6',
    description: 'Моделирование освещенности рельефа' 
  },
  { 
    name: 'viewshed_analysis', 
    title: 'Зоны видимости', 
    icon: 'mdi-eye-outline',
    description: 'Определение видимости с учетом высоты объекта' 
  },
  { 
    name: 'zonal_statistics', 
    title: 'Зональная статистика', 
    icon: 'mdi-table-large',
    description: 'Расчет статистики растра по зонам' 
  },
  { 
    name: 'clip_raster_by_mask', 
    title: 'Обрезка растра', 
    icon: 'mdi-crop-free',
    description: 'Обрезка растра по векторной маске' 
  }
];

function onToolClick(pluginName: string) {
  emit('select-tool', pluginName);
  menuOpen.value = false;
}
</script>

<template>
  <div class="analysis-menu-container">
    <v-menu v-model="menuOpen" :close-on-content-click="false" location="bottom end">
      <template v-slot:activator="{ props }">
        <v-btn
          v-bind="props"
          icon="mdi-google-analytics"
          color="white"
          class="analysis-btn"
          title="Инструменты геоаналитики"
        ></v-btn>
      </template>

      <v-list width="300" class="pa-2">
        <v-list-subheader class="text-uppercase font-weight-bold">Геоаналитика</v-list-subheader>
        
        <v-list-item
          v-for="tool in tools"
          :key="tool.name"
          :prepend-icon="tool.icon"
          @click="onToolClick(tool.name)"
          rounded="lg"
          class="mb-1"
        >
          <v-list-item-title>{{ tool.title }}</v-list-item-title>
          <v-list-item-subtitle>{{ tool.description }}</v-list-item-subtitle>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>

<style scoped>
.analysis-menu-container {
  pointer-events: auto;
}
.analysis-btn {
  box-shadow: 0 2px 8px rgba(0,0,0,0.2) !important;
}
</style>
