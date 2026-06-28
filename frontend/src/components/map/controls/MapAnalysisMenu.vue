<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits(['select-tool']);

const menuOpen = ref(false);

const categorizedTools = [
  {
    title: 'Анализ рельефа и поверхностей',
    items: [
      { name: 'terrain_contours', title: 'Изолинии рельефа', icon: 'mdi-chart-bell-curve', description: 'Генерация контуров высот из DEM' },
      { name: 'slope', title: 'Крутизна уклонов', icon: 'mdi-slope-uphill', description: 'Расчет углов наклона поверхности' },
      { name: 'aspect', title: 'Направление экспозиции', icon: 'mdi-compass-outline', description: 'Определение ориентации уклонов' },
      { name: 'hillshade', title: 'Теневая отмывка', icon: 'mdi-brightness-6', description: 'Моделирование освещенности рельефа' },
      { name: 'viewshed_analysis', title: 'Зоны видимости', icon: 'mdi-eye-outline', description: 'Определение видимости с высоты' }
    ]
  },
  {
    title: 'Дистанционное зондирование и растры',
    items: [
      { name: 'spectral_indices', title: 'Спектральные индексы', icon: 'mdi-math-compass', description: 'Расчет NDVI, NDWI, NBR, NDRE по каналам' },
      { name: 'raster_reclass', title: 'Реклассификация растра', icon: 'mdi-palette-swatch-outline', description: 'Переопределение значений пикселей' },
      { name: 'raster_algebra', title: 'Алгебра растров', icon: 'mdi-calculator-variant', description: 'Вычисления над растровыми слоями' }
    ]
  },
  {
    title: 'Векторно-растровые преобразования',
    items: [
      { name: 'import_dxf', title: 'Импорт DXF', icon: 'mdi-file-cad', description: 'Импорт слоев DXF чертежей в векторный GeoJSON' },
      { name: 'polygonize_raster', title: 'Векторизация растра', icon: 'mdi-vector-polygon', description: 'Преобразование растра в векторный формат' },
      { name: 'rasterize_vector', title: 'Растеризация вектора', icon: 'mdi-grid-large', description: 'Преобразование вектора в растровый формат' },
      { name: 'clip_raster_by_mask', title: 'Обрезка растра', icon: 'mdi-crop-free', description: 'Обрезка растра по векторной маске' },
      { name: 'raster_mosaic', title: 'Мозаика растров', icon: 'mdi-checkerboard', description: 'Сшивка растровых тайлов в мозаику' }
    ]
  },
  {
    title: 'Продвинутая пространственная аналитика',
    items: [
      { name: 'zonal_statistics', title: 'Зональная статистика', icon: 'mdi-table-large', description: 'Расчет статистики растра по зонам' },
      { name: 'unsupervised_class', title: 'Классификация K-Means', icon: 'mdi-chart-scatter-plot', description: 'Неконтролируемая классификация растра' },
      { name: 'watershed_delineation', title: 'Выделение водосборов', icon: 'mdi-water-percent', description: 'Расчет бассейнов стока и водотоков по DEM' }
    ]
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

      <v-list width="350" class="pa-2 dropdown-scroll" max-height="550" style="overflow-y: auto;">
        <v-list-subheader class="text-uppercase font-weight-bold text-grey-darken-2">Геоаналитика</v-list-subheader>
        
        <template v-for="category in categorizedTools" :key="category.title">
          <v-divider class="my-1"></v-divider>
          <v-list-subheader class="font-weight-bold text-primary text-caption pb-1">{{ category.title }}</v-list-subheader>
          
          <v-list-item
            v-for="tool in category.items"
            :key="tool.name"
            :prepend-icon="tool.icon"
            @click="onToolClick(tool.name)"
            rounded="lg"
            class="mb-1 py-1"
            density="compact"
          >
            <v-list-item-title class="font-weight-medium" style="font-size: 13px;">{{ tool.title }}</v-list-item-title>
            <v-list-item-subtitle style="font-size: 11px; line-height: 1.2;">{{ tool.description }}</v-list-item-subtitle>
          </v-list-item>
        </template>
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
.dropdown-scroll::-webkit-scrollbar {
  width: 6px;
}
.dropdown-scroll::-webkit-scrollbar-thumb {
  background-color: rgba(0, 0, 0, 0.15);
  border-radius: 4px;
}
</style>
