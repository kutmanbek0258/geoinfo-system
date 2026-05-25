<template>
  <v-dialog v-model="dialog" max-width="500px">
    <template v-slot:activator="{ props }">
      <v-btn v-bind="props" icon color="primary" class="ml-2" title="Печать карты">
        <v-icon>mdi-printer</v-icon>
      </v-btn>
    </template>

    <v-card>
      <v-card-title>Настройка печати</v-card-title>
      <v-card-text>
        <v-select
          v-model="layout"
          :items="layouts"
          label="Макет"
          item-title="title"
          item-value="value"
        ></v-select>

        <v-radio-group v-model="selectionMode" label="Область печати">
          <v-radio label="Текущий вид" value="VIEW"></v-radio>
          <v-radio label="Выделенная область" value="AREA"></v-radio>
        </v-radio-group>

        <v-text-field v-model="title" label="Заголовок отчета"></v-text-field>
        <v-text-field v-model="author" label="Автор"></v-text-field>

        <v-alert v-if="task" :type="taskStatusType" class="mt-2">
          Статус: {{ task.status }}
          <v-progress-linear v-if="task.status === 'PROCESSING' || task.status === 'PENDING'" indeterminate></v-progress-linear>
          <v-btn v-if="task.status === 'COMPLETED'" :href="task.s3Url" target="_blank" color="success" class="mt-2">
            Скачать PDF
          </v-btn>
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="grey" variant="text" @click="dialog = false">Закрыть</v-btn>
        <v-btn color="primary" :loading="loading" @click="startPrint">Запустить печать</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { Map } from 'ol';
import Layer from 'ol/layer/Layer';
import TileWMS from 'ol/source/TileWMS';
import VectorSource from 'ol/source/Vector';
import GeoJSON from 'ol/format/GeoJSON';
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

const layouts = [
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

const startPrint = async () => {
  if (!props.map) return;
  loading.value = true;
  task.value = null;

  try {
    const view = props.map.getView();
    let extent: number[];

    if (selectionMode.value === 'VIEW') {
      extent = view.calculateExtent(props.map.getSize());
    } else {
      // Для AREA режима в идеале нужен Interaction, здесь упрощаем до текущего вида
      // В реальном приложении здесь была бы логика выбора рамки
      extent = view.calculateExtent(props.map.getSize());
    }

    const layers = props.map.getLayers().getArray()
      .filter(l => l.getVisible())
      .map(l => {
        if (!(l instanceof Layer)) return null;
        const source = l.getSource();
        if (source instanceof TileWMS) {
          let wmsUrl = source.getUrls() ? source.getUrls()![0] : (source as any).getUrl();
          // Заменяем localhost на имя сервиса для доступа внутри Docker сети
          wmsUrl = wmsUrl.replace('localhost', 'nginx-proxy');
          
          return {
            type: 'WMS',
            url: wmsUrl,
            layerName: source.getParams().LAYERS,
            opacity: l.getOpacity()
          };
        }
        if (source instanceof VectorSource) {
          const format = new GeoJSON();
          return {
            type: 'VECTOR',
            features: format.writeFeaturesObject(source.getFeatures()),
            style: { strokeColor: '#FF0000', strokeWidth: 2 }
          };
        }
        return null;
      })
      .filter(l => l !== null);

    const spec: PrintSpecification = {
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
