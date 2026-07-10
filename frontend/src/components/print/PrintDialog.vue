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

        <div class="mb-4">
          <v-btn 
            color="primary" 
            variant="outlined"
            block 
            @click="toggleSelection"
            prepend-icon="mdi-vector-selection"
          >
            {{ selectedExtent ? 'Изменить область печати' : 'Выбрать область на карте' }}
          </v-btn>
          <div v-if="selectedExtent && !isSelecting" class="text-caption text-success mt-2 text-center d-flex align-center justify-center">
            <v-icon size="small" color="success" class="mr-1">mdi-check-circle</v-icon>
            Область печати выбрана и зафиксирована
          </div>
          <div v-else-if="!selectedExtent && !isSelecting" class="text-caption text-error mt-2 text-center d-flex align-center justify-center">
            <v-icon size="small" color="error" class="mr-1">mdi-alert-circle</v-icon>
            Необходимо выбрать область на карте перед печатью
          </div>
        </div>

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
          :disabled="!selectedExtent || loading"
          @click="startPrint"
        >
          Запустить печать
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <!-- Floating Viewfinder / Shot Frame overlay -->
  <teleport to="body">
    <div v-if="isSelecting" class="shot-frame-mask">
      <!-- Transparent viewport in the center -->
      <div :style="viewportStyle" class="shot-frame-viewport">
        <!-- Subtle crosshairs/grid inside the viewport -->
        <div class="shot-frame-grid"></div>
        <!-- Visual corners -->
        <div class="corner top-left"></div>
        <div class="corner top-right"></div>
        <div class="corner bottom-left"></div>
        <div class="corner bottom-right"></div>
      </div>

      <!-- Action banner/buttons at the bottom -->
      <div class="shot-frame-controls">
        <div class="text-subtitle-1 mb-2 font-weight-medium">
          Подгоните нужную область в рамку (перемещая и масштабируя карту)
        </div>
        <div class="d-flex justify-center gap-2">
          <v-btn color="grey-darken-3" class="text-white px-4" @click="cancelSelection">Отмена</v-btn>
          <v-btn color="success" prepend-icon="mdi-check" class="px-4" @click="confirmSelection">Выбрать область</v-btn>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted, toRaw } from 'vue';
import { useStore } from 'vuex';
import type { Map } from 'ol';
import Layer from 'ol/layer/Layer';
import TileWMS from 'ol/source/TileWMS';
import VectorSource from 'ol/source/Vector';
import GeoJSON from 'ol/format/GeoJSON';
import VectorLayer from 'ol/layer/Vector';
import VectorTileLayer from 'ol/layer/VectorTile';
import Feature from 'ol/Feature';
import { fromExtent } from 'ol/geom/Polygon';
import Style from 'ol/style/Style';
import Stroke from 'ol/style/Stroke';
import printService, { type PrintTask, type PrintSpecification } from '@/services/print.service';

const props = defineProps<{
  map: Map | null;
}>();

const store = useStore();
const dialog = ref(false);
const loading = ref(false);
const layout = ref('A4_LANDSCAPE');
const selectionMode = ref('AREA');
const title = ref('Геопространственный отчет');
const author = ref('Пользователь');
const task = ref<PrintTask | null>(null);

// Состояния для выделения области
const isSelecting = ref(false);
const selectedExtent = ref<number[] | null>(null);
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
    dialog.value = true;
  } else {
    startSelection();
  }
};

const startSelection = () => {
  isSelecting.value = true;
  dialog.value = false; // Скрываем диалог настроек
};

const stopSelection = () => {
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

// Вычисление соотношения сторон карты в PDF на основе выбранного формата
const getMapAspectRatio = () => {
  const sizeMap: Record<string, [number, number]> = {
    'A0': [2383.94, 3370.39],
    'A1': [1683.78, 2383.94],
    'A2': [1190.55, 1683.78],
    'A3': [841.89, 1190.55],
    'A4': [595.28, 841.89]
  };

  let pageFormat = 'A3';
  let isLandscape = true;

  const parts = layout.value.split('_');
  if (parts.length >= 1) {
    pageFormat = parts[0];
  }
  if (parts.length >= 2 && parts[1] === 'PORTRAIT') {
    isLandscape = false;
  }

  const dims = sizeMap[pageFormat] || sizeMap['A3'];
  const W_page = isLandscape ? dims[1] : dims[0];
  const H_page = isLandscape ? dims[0] : dims[1];

  const margin = 14.17; // 0.5 cm
  const W_avail = W_page - (2 * margin);
  const H_avail = H_page - (2 * margin);
  const H_map = H_avail * 0.80; // Карта занимает 80% контента

  return W_avail / H_map;
};

// Динамические стили для центрированной рамки видоискателя
const viewportStyle = computed(() => {
  const aspect = getMapAspectRatio();
  const maxW = window.innerWidth * 0.8;
  const maxH = window.innerHeight * 0.7;

  let width, height;
  if (maxW / aspect <= maxH) {
    width = maxW;
    height = maxW / aspect;
  } else {
    height = maxH;
    width = maxH * aspect;
  }

  return {
    width: `${width}px`,
    height: `${height}px`,
    boxShadow: '0 0 0 9999px rgba(0, 0, 0, 0.4)',
  };
});

// Подтверждение выбора области (конвертация экранных пикселей в гео-координаты)
const confirmSelection = () => {
  if (!props.map) return;

  const aspect = getMapAspectRatio();
  const maxW = window.innerWidth * 0.8;
  const maxH = window.innerHeight * 0.7;

  let width, height;
  if (maxW / aspect <= maxH) {
    width = maxW;
    height = maxW / aspect;
  } else {
    height = maxH;
    width = maxH * aspect;
  }

  const left = (window.innerWidth - width) / 2;
  const top = (window.innerHeight - height) / 2;
  const right = left + width;
  const bottom = top + height;

  const mapElement = props.map.getTargetElement();
  if (!mapElement) return;

  const mapRect = mapElement.getBoundingClientRect();
  const mapLeft = left - mapRect.left;
  const mapTop = top - mapRect.top;
  const mapRight = right - mapRect.left;
  const mapBottom = bottom - mapRect.top;

  const topLeftCoord = props.map.getCoordinateFromPixel([mapLeft, mapTop]);
  const bottomRightCoord = props.map.getCoordinateFromPixel([mapRight, mapBottom]);

  if (topLeftCoord && bottomRightCoord) {
    const minX = Math.min(topLeftCoord[0], bottomRightCoord[0]);
    const minY = Math.min(topLeftCoord[1], bottomRightCoord[1]);
    const maxX = Math.max(topLeftCoord[0], bottomRightCoord[0]);
    const maxY = Math.max(topLeftCoord[1], bottomRightCoord[1]);

    selectedExtent.value = [minX, minY, maxX, maxY];

    // Рисуем пунктирную рамку на карте
    if (!selectionLayer) {
      selectionLayer = new VectorLayer({
        source: new VectorSource(),
        properties: { name: 'selection-layer' }
      });
      props.map.addLayer(selectionLayer);
    }
    selectionLayer.getSource()?.clear();

    const rectFeature = new Feature({
      geometry: fromExtent(selectedExtent.value)
    });
    rectFeature.setStyle(new Style({
      stroke: new Stroke({
        color: '#ffc107',
        width: 3,
        lineDash: [6, 6]
      })
    }));
    selectionLayer.getSource()?.addFeature(rectFeature);
  }

  stopSelection();
  dialog.value = true;
};

const cancelSelection = () => {
  stopSelection();
  dialog.value = true;
};

// Очистка при смене формата или закрытии диалога
watch(layout, () => {
  cleanupSelection();
});

watch(dialog, (val) => {
  if (!val && !isSelecting.value) {
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

    let hasMvt = false;
    let mvtOpacity = 1.0;

    const parsedLayers = props.map.getLayers().getArray()
      .slice() // Клонируем массив, чтобы не менять порядок на живой карте
      .filter(lProxy => {
        const l = toRaw(lProxy);
        if (!l) return false;
        const visible = typeof l.getVisible === 'function' ? l.getVisible() : true;
        const name = typeof l.getProperties === 'function' ? l.getProperties().name : '';
        return visible && name !== 'selection-layer';
      })
      .sort((a, b) => {
        const az = typeof a.getZIndex === 'function' ? (a.getZIndex() || 0) : 0;
        const bz = typeof b.getZIndex === 'function' ? (b.getZIndex() || 0) : 0;
        return az - bz;
      })
      .flatMap(lProxy => {
        const l = toRaw(lProxy);
        if (!l || typeof (l as any).getSource !== 'function') return [];

        if (l instanceof VectorTileLayer) {
          hasMvt = true;
          mvtOpacity = l.getOpacity();
          return [];
        }

        const source = toRaw((l as any).getSource());
        if (!source) return [];

        const properties = typeof l.getProperties === 'function' ? l.getProperties() : {};

        // Check if source is XYZ (TiTiler layer / COG)
        if (typeof source.getUrls === 'function' || typeof source.getUrl === 'function') {
          let tileUrl = '';
          if (typeof source.getUrls === 'function' && source.getUrls()) {
            tileUrl = source.getUrls()[0];
          } else if (typeof source.getUrl === 'function') {
            tileUrl = source.getUrl();
          }

          if (tileUrl && tileUrl.includes('/raster/cog/')) {
            let relativeKey = '';
            let colormap = null;
            let colormapName = null;
            let resampling = null;

            try {
              const urlParts = tileUrl.split('?');
              if (urlParts.length > 1) {
                const searchParams = new URLSearchParams(urlParts[1]);
                const urlParam = searchParams.get('url');
                if (urlParam) {
                  relativeKey = decodeURIComponent(urlParam)
                    .replace('s3://geo-abstraction-input/', '')
                    .replace('s3://', '');
                }
                colormap = searchParams.get('colormap');
                colormapName = searchParams.get('colormap_name');
                resampling = searchParams.get('resampling');
              }
            } catch (e) {
              console.error('Failed to parse tile URL parameters:', e);
            }

            if (!relativeKey && properties.cogObjectKey) {
              relativeKey = properties.cogObjectKey;
            }

            if (relativeKey) {
              return [{
                type: 'COG',
                url: relativeKey,
                layerName: properties.title || properties.name || 'Raster Layer',
                opacity: l.getOpacity(),
                colormap: colormap,
                colormapName: colormapName,
                resampling: resampling
              }];
            }
          }
        }

        // 3. Check if source is VectorSource (Vector Layer)
        if (typeof source.getFeatures === 'function') {
          const allFeatures = source.getFeatures();
          const geometryGroups = [
            { types: ['Polygon', 'MultiPolygon'], name: 'POLYGONS' },
            { types: ['LineString', 'MultiLineString'], name: 'LINES' },
            { types: ['Point', 'MultiPoint'], name: 'POINTS' }
          ];

          return geometryGroups.map(group => {
            const groupFeatures = allFeatures.filter((f: any) => {
              const geom = typeof f.getGeometry === 'function' ? f.getGeometry() : null;
              const type = geom ? geom.getType() : '';
              return group.types.includes(type);
            });

            if (groupFeatures.length === 0) return null;

            const firstFeature = groupFeatures[0];
            const styleData = typeof firstFeature.get === 'function' ? firstFeature.get('style') : null;

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
            const cleanFeatures = groupFeatures.map((f: any) => {
              const clone = f.clone();
              const geometryName = typeof clone.getGeometryName === 'function' ? clone.getGeometryName() : 'geometry';
              const properties = typeof clone.getProperties === 'function' ? clone.getProperties() : {};
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

    const finalLayers = [...parsedLayers];
    if (hasMvt) {
      const isFeatureVisible = (feat: any) => {
        let char = feat.characteristics;
        if (typeof char === 'string') {
          try {
            char = JSON.parse(char);
          } catch (e) {
            return true;
          }
        }
        if (char) {
          return char.visible !== false && char.isVisible !== false;
        }
        return true;
      };

      const pointIds = (store.state.geodata?.points || [])
        .filter(isFeatureVisible)
        .map((p: any) => p.id)
        .filter(Boolean);

      const multilineIds = (store.state.geodata?.multilines || [])
        .filter(isFeatureVisible)
        .map((l: any) => l.id)
        .filter(Boolean);

      const polygonIds = (store.state.geodata?.polygons || [])
        .filter(isFeatureVisible)
        .map((p: any) => p.id)
        .filter(Boolean);

      finalLayers.push({
        type: 'VECTOR',
        layerName: 'Project Vectors',
        opacity: mvtOpacity,
        pointIds,
        multilineIds,
        polygonIds,
        layerStyle: {
          strokeColor: '#3399CC',
          strokeWidth: 2,
          fillColor: '#3399CC',
          fillOpacity: 0.4
        }
      } as any);
    }

    const spec: PrintSpecification = {
      projectId: props.map.get('projectId') || (props.map as any).getProperties().projectId,
      layout: layout.value,
      dpi: 300,
      mapContext: {
        projection: view.getProjection().getCode(),
        bbox: extent,
        rotation: view.getRotation()
      },
      layers: finalLayers as any[],
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

.shot-frame-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 99999;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
}

.shot-frame-viewport {
  position: relative;
  border: 3px solid #ffc107;
  box-sizing: border-box;
  pointer-events: none;
}

.shot-frame-grid {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border: 1px dashed rgba(255, 193, 7, 0.25);
  background: 
    linear-gradient(to right, rgba(255, 193, 7, 0.15) 33.3%, transparent 33.3%, transparent 66.6%, rgba(255, 193, 7, 0.15) 66.6%),
    linear-gradient(to bottom, rgba(255, 193, 7, 0.15) 33.3%, transparent 33.3%, transparent 66.6%, rgba(255, 193, 7, 0.15) 66.6%);
  pointer-events: none;
}

.corner {
  position: absolute;
  width: 20px;
  height: 20px;
  border: 4px solid #ffc107;
  pointer-events: none;
}
.top-left { top: -4px; left: -4px; border-right: 0; border-bottom: 0; }
.top-right { top: -4px; right: -4px; border-left: 0; border-bottom: 0; }
.bottom-left { bottom: -4px; left: -4px; border-right: 0; border-top: 0; }
.bottom-right { bottom: -4px; right: -4px; border-left: 0; border-top: 0; }

.shot-frame-controls {
  position: absolute;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(33, 33, 33, 0.95);
  color: white;
  padding: 16px 24px;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
  text-align: center;
  z-index: 100000;
  pointer-events: auto;
}
</style>
