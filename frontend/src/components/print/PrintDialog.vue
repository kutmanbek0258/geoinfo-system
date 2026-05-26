<template>
  <v-dialog v-model="dialog" max-width="500px" persistent>
    <template v-slot:activator="{ props }">
      <v-btn v-bind="props" icon color="primary" class="ml-2" title="Печать карты">
        <v-icon>mdi-printer</v-icon>
      </v-btn>
    </template>

    <v-card>
      <v-card-title class="d-flex justify-space-between align-center">
        <span>Настройка печати</span>
        <v-btn icon="mdi-close" variant="text" @click="closeDialog"></v-btn>
      </v-card-title>
      
      <v-card-text>
        <v-select
          v-model="layout"
          :items="layouts"
          label="Формат и ориентация"
          item-title="title"
          item-value="value"
          variant="outlined"
          density="comfortable"
        ></v-select>

        <v-radio-group v-model="selectionMode" label="Область печати" inline>
          <v-radio label="Текущий вид" value="VIEW"></v-radio>
          <v-radio label="Выбрать область" value="AREA"></v-radio>
        </v-radio-group>

        <v-expand-transition>
          <div v-if="selectionMode === 'AREA'" class="mb-4">
            <v-btn 
              :color="isSelecting ? 'warning' : 'secondary'" 
              block 
              @click="toggleSelection"
              :prepend-icon="isSelecting ? 'mdi-stop' : 'mdi-vector-selection'"
            >
              {{ isSelecting ? 'Завершить выбор' : (selectedExtent ? 'Выбрать заново' : 'Нарисовать рамку') }}
            </v-btn>
            <div v-if="selectedExtent && !isSelecting" class="text-caption text-success mt-1 text-center">
              <v-icon size="small">mdi-check-circle</v-icon> Область выбрана
            </div>
          </div>
        </v-expand-transition>

        <v-text-field v-model="title" label="Заголовок отчета" variant="outlined" density="comfortable"></v-text-field>
        <v-text-field v-model="author" label="Автор" variant="outlined" density="comfortable"></v-text-field>

        <v-alert v-if="task" :type="taskStatusType" variant="tonal" class="mt-2">
          Статус: {{ taskStatusLabel }}
          <v-progress-linear v-if="task.status === 'PROCESSING' || task.status === 'PENDING'" indeterminate color="primary" class="mt-2"></v-progress-linear>
          <v-btn v-if="task.status === 'COMPLETED'" :href="task.s3Url" target="_blank" color="success" block class="mt-4" prepend-icon="mdi-download">
            Скачать PDF
          </v-btn>
          <div v-if="task.status === 'FAILED'" class="text-caption mt-1">{{ task.errorMessage }}</div>
        </v-alert>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions class="pa-4">
        <v-spacer></v-spacer>
        <v-btn color="grey" variant="text" @click="closeDialog">Отмена</v-btn>
        <v-btn 
          color="primary" 
          variant="elevated" 
          :loading="loading" 
          :disabled="selectionMode === 'AREA' && !selectedExtent && !isSelecting"
          @click="startPrint"
        >
          Запустить печать
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import type { Map } from 'ol';
import Layer from 'ol/layer/Layer';
import TileWMS from 'ol/source/TileWMS';
import VectorSource from 'ol/source/Vector';
import GeoJSON from 'ol/format/GeoJSON';
import Draw from 'ol/interaction/Draw';
import VectorLayer from 'ol/layer/Vector';
import { createBox } from 'ol/interaction/Draw';
import printService, { type PrintTask, type PrintSpecification } from '@/services/print.service';

const props = defineProps<{
  map: Map | null;
}>();

const dialog = ref(false);
const loading = ref(false);
const layout = ref('A4_LANDSCAPE');
const selectionMode = ref('VIEW');
const title = ref('Геопространственный отчет');
const author = ref('Пользователь');
const task = ref<PrintTask | null>(null);

// Состояния для выделения области
const isSelecting = ref(false);
const selectedExtent = ref<number[] | null>(null);
let drawInteraction: Draw | null = null;
let selectionLayer: VectorLayer<VectorSource> | null = null;

const layouts = [
  { title: 'A0 Альбомная', value: 'A0_LANDSCAPE' },
  { title: 'A0 Книжная', value: 'A0_PORTRAIT' },
  { title: 'A1 Альбомная', value: 'A1_LANDSCAPE' },
  { title: 'A1 Книжная', value: 'A1_PORTRAIT' },
  { title: 'A2 Альбомная', value: 'A2_LANDSCAPE' },
  { title: 'A2 Книжная', value: 'A2_PORTRAIT' },
  { title: 'A3 Альбомная', value: 'A3_LANDSCAPE' },
  { title: 'A3 Книжная', value: 'A3_PORTRAIT' },
  { title: 'A4 Альбомная', value: 'A4_LANDSCAPE' },
  { title: 'A4 Книжная', value: 'A4_PORTRAIT' },
];

const taskStatusType = computed(() => {
  if (!task.value) return 'info';
  switch (task.value.status) {
    case 'COMPLETED': return 'success';
    case 'FAILED': return 'error';
    case 'PROCESSING':
    case 'PENDING': return 'warning';
    default: return 'info';
  }
});

const taskStatusLabel = computed(() => {
  if (!task.value) return '';
  const labels: Record<string, string> = {
    'PENDING': 'В очереди',
    'PROCESSING': 'Выполняется рендеринг...',
    'COMPLETED': 'Готово',
    'FAILED': 'Ошибка'
  };
  return labels[task.value.status] || task.value.status;
});

// Управление взаимодействием выбора области
const toggleSelection = () => {
  if (!props.map) return;

  if (isSelecting.value) {
    stopSelection();
  } else {
    startSelection();
  }
};

const startSelection = () => {
  if (!props.map) return;
  
  isSelecting.value = true;
  selectedExtent.value = null;

  // Создаем слой для отображения рамки, если его нет
  if (!selectionLayer) {
    selectionLayer = new VectorLayer({
      source: new VectorSource(),
      properties: { name: 'selection-layer' }
    });
    props.map.addLayer(selectionLayer);
  }
  selectionLayer.getSource()?.clear();

  drawInteraction = new Draw({
    source: selectionLayer.getSource()!,
    type: 'Circle',
    geometryFunction: createBox(),
  });

  drawInteraction.on('drawend', (event) => {
    const extent = event.feature.getGeometry()?.getExtent();
    if (extent) {
      selectedExtent.value = extent;
    }
    stopSelection();
  });

  props.map.addInteraction(drawInteraction);
};

const stopSelection = () => {
  if (drawInteraction && props.map) {
    props.map.removeInteraction(drawInteraction);
    drawInteraction = null;
  }
  isSelecting.value = false;
};

const cleanupSelection = () => {
  stopSelection();
  if (selectionLayer && props.map) {
    props.map.removeLayer(selectionLayer);
    selectionLayer = null;
  }
  selectedExtent.value = null;
};

const closeDialog = () => {
  dialog.value = false;
  cleanupSelection();
  task.value = null;
};

// Следим за изменением режима: если переключились с AREA, чистим карту
watch(selectionMode, (newMode) => {
  if (newMode !== 'AREA') {
    cleanupSelection();
  }
});

// Следим за открытием диалога
watch(dialog, (val) => {
  if (!val) {
    cleanupSelection();
  }
});

onUnmounted(() => {
  cleanupSelection();
});

const startPrint = async () => {
  if (!props.map) return;
  loading.value = true;
  task.value = null;

  try {
    const view = props.map.getView();
    let extent: number[];

    if (selectionMode.value === 'AREA' && selectedExtent.value) {
      extent = selectedExtent.value;
    } else {
      extent = view.calculateExtent(props.map.getSize());
    }

    const layers = props.map.getLayers().getArray()
      .slice() // Клонируем массив, чтобы не менять порядок на живой карте
      .filter(l => l.getVisible() && l.getProperties().name !== 'selection-layer')
      .sort((a, b) => (a.getZIndex() || 0) - (b.getZIndex() || 0)) // Сортируем по z-index (от нижних к верхним)
      .flatMap(l => {
        if (!(l instanceof Layer)) return [];
        const source = l.getSource();
        if (source instanceof TileWMS) {
          let wmsUrl = source.getUrls() ? source.getUrls()![0] : (source as any).getUrl();
          wmsUrl = wmsUrl.replace('localhost', 'nginx-proxy');
          
          return [{
            type: 'WMS',
            url: wmsUrl,
            layerName: source.getParams().LAYERS,
            opacity: l.getOpacity()
          }];
        }
        if (source instanceof VectorSource) {
          const allFeatures = source.getFeatures();
          // Группируем фичи по типу геометрии для корректного рендеринга на бэкенде
          const geometryGroups = [
            { types: ['Polygon', 'MultiPolygon'], name: 'POLYGONS' },
            { types: ['LineString', 'MultiLineString'], name: 'LINES' },
            { types: ['Point', 'MultiPoint'], name: 'POINTS' }
          ];

          return geometryGroups.map(group => {
            const groupFeatures = allFeatures.filter(f => {
              const type = f.getGeometry()?.getType() || '';
              return group.types.includes(type);
            });

            if (groupFeatures.length === 0) return null;

            // Извлекаем стиль из первого объекта данной группы
            const firstFeature = groupFeatures[0];
            const styleData = firstFeature?.get('style');

            let strokeColor = '#3399CC';
            let strokeWidth = 2;
            let fillColor = '#3399CC';
            let fillOpacity = 0.4;

            if (styleData) {
              if (styleData.line?.color) strokeColor = styleData.line.color;
              if (styleData.line?.width) strokeWidth = styleData.line.width;
              if (styleData.poly?.fillColor) {
                const rgba = styleData.poly.fillColor;
                if (rgba.startsWith('rgba')) {
                  const parts = rgba.replace('rgba(', '').replace(')', '').split(',');
                  if (parts.length === 4) {
                    const r = parseInt(parts[0].trim());
                    const g = parseInt(parts[1].trim());
                    const b = parseInt(parts[2].trim());
                    fillColor = '#' + (1 << 24 | r << 16 | g << 8 | b).toString(16).slice(1).toUpperCase();
                    fillOpacity = parseFloat(parts[3].trim());
                  }
                } else {
                  fillColor = rgba;
                  fillOpacity = 1.0;
                }
              }
            }

            const format = new GeoJSON();
            // Клонируем фичеры и удаляем все служебные атрибуты
            const cleanFeatures = groupFeatures.map(f => {
              const clone = f.clone();
              const geometryName = clone.getGeometryName();
              // Оставляем только базовые метаданные и геометрию, удаляем 'style' и другие объекты
              const properties = clone.getProperties();
              for (const key in properties) {
                if (key !== geometryName && (key === 'style' || typeof properties[key] === 'object')) {
                  clone.unset(key);
                }
              }
              return clone;
            });

            return {
              type: 'VECTOR',
              features: format.writeFeaturesObject(cleanFeatures, {
                featureProjection: view.getProjection(),
                dataProjection: 'EPSG:4326'
              }),
              layerStyle: {
                strokeColor,
                strokeWidth,
                fillColor,
                fillOpacity
              }
            };
          }).filter((v): v is any => v !== null);
        }
        return [];
      });

    const spec: PrintSpecification = {
      projectId: props.map.get('projectId') || (props.map as any).getProperties().projectId,
      layout: layout.value,
      dpi: 300,
      mapContext: {
        projection: view.getProjection().getCode(),
        bbox: extent,
        rotation: view.getRotation()
      },
      layers: layers as any[],
      attributes: {
        title: title.value,
        author: author.value,
        organization: 'ГеоИнфоСистема'
      }
    };

    const newTask = await printService.createPrintTask(spec);
    task.value = newTask;
    pollTaskStatus(newTask.id);
  } catch (e) {
    console.error('Print failed', e);
  } finally {
    loading.value = false;
  }
};

const pollTaskStatus = async (id: string) => {
  const interval = setInterval(async () => {
    if (!dialog.value) {
        clearInterval(interval);
        return;
    }
    try {
      const updatedTask = await printService.getPrintTask(id);
      task.value = updatedTask;
      if (updatedTask.status === 'COMPLETED' || updatedTask.status === 'FAILED') {
        clearInterval(interval);
      }
    } catch (e) {
      clearInterval(interval);
    }
  }, 2000);
};
</script>

<style scoped>
.v-card-title {
  border-bottom: 1px solid rgba(0,0,0,0.1);
}
</style>
