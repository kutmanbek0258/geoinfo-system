<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { CreateAnalysisTaskDto, AnalysisDataSource, Document } from '@/types/api';

const props = defineProps<{
  show: boolean;
}>();

const emit = defineEmits(['update:show', 'task-created']);

const store = useStore();
const valid = ref(false);
const loading = ref(false);
const sourceCrs = ref('');

const internalShow = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
});

const formData = ref({
  selectedDocument: null as Document | null,
});

// Запрашиваем документы проекта при открытии диалога
watch(() => props.show, (newVal) => {
  if (newVal) {
    const projectId = store.state.geodata.selectedProjectId;
    if (projectId) {
      store.dispatch('geodata/fetchDocuments', projectId);
    }
  }
});

// Фильтруем прикрепленные файлы проекта, оставляя только расширение .dxf
const dxfDocuments = computed(() => {
  const docs = store.state.geodata.documents || [];
  return docs.filter((doc: Document) => doc.fileName.toLowerCase().endsWith('.dxf'));
});

async function runAnalysis() {
  if (!formData.value.selectedDocument) return;
  
  loading.value = true;
  try {
    const doc = formData.value.selectedDocument;
    
    // Формируем S3 URL для документа
    const s3Url = `s3://documents/${doc.minioObjectKey}`;
    
    const inputs: Record<string, AnalysisDataSource> = {
      'dxf_file': {
        type: 'DIRECT_S3',
        s3Url: s3Url
      }
    };

    const dto: CreateAnalysisTaskDto = {
      pluginName: 'import_dxf',
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs,
      parameters: {
        source_crs: sourceCrs.value ? sourceCrs.value.trim() : undefined
      }
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
    
    // Очистка формы
    formData.value.selectedDocument = null;
    sourceCrs.value = '';
  } catch (err) {
    console.error('Failed to run DXF import task:', err);
    alert('Не удалось запустить задачу импорта DXF.');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <v-dialog v-model="internalShow" max-width="550px">
    <v-card class="dxf-import-card">
      <v-card-title class="pa-4 bg-primary text-white d-flex align-center">
        <v-icon start icon="mdi-file-cad" class="mr-2"></v-icon>
        Импорт DXF чертежа
      </v-card-title>
      
      <v-card-text class="pa-4">
        <v-form ref="form" v-model="valid">
          <p class="text-subtitle-2 text-grey-darken-1 mb-4">
            Этот инструмент конвертирует векторные слои DXF чертежей в GeoJSON формат проекта. Z-координаты высот и глубин будут полностью сохранены.
          </p>

          <!-- Предупреждение, если DXF файлы не найдены -->
          <v-alert
            v-if="dxfDocuments.length === 0"
            type="warning"
            variant="tonal"
            class="mb-4"
            density="comfortable"
          >
            В прикрепленных файлах проекта не найдено файлов с расширением .dxf. Загрузите файлы DXF через свойства проекта (левый верхний угол карты).
          </v-alert>

          <!-- Выбор документа DXF -->
          <v-select
            v-model="formData.selectedDocument"
            :items="dxfDocuments"
            item-title="fileName"
            return-object
            label="Выберите DXF файл *"
            placeholder="Выберите прикрепленный файл .dxf"
            prepend-inner-icon="mdi-file-cad"
            variant="outlined"
            density="comfortable"
            :rules="[v => !!v || 'Необходимо выбрать файл']"
            :disabled="dxfDocuments.length === 0 || loading"
            class="mb-3"
          >
            <template v-slot:item="{ props, item }">
              <v-list-item v-bind="props" :subtitle="(item.raw as any).description || 'Без описания'"></v-list-item>
            </template>
          </v-select>

          <!-- Код исходной CRS -->
          <v-text-field
            v-model="sourceCrs"
            label="Система координат чертежа (CRS источника)"
            placeholder="например, EPSG:32643 или EPSG:3857"
            prepend-inner-icon="mdi-earth"
            variant="outlined"
            density="comfortable"
            hint="Если в DXF отсутствует внутренняя геопривязка, укажите СК здесь (например, код зоны UTM)"
            persistent-hint
            :disabled="loading"
            class="mb-2"
          ></v-text-field>
        </v-form>
      </v-card-text>

      <v-card-actions class="px-4 py-3 bg-grey-lighten-4">
        <v-spacer></v-spacer>
        <v-btn
          color="grey-darken-1"
          variant="text"
          :disabled="loading"
          @click="internalShow = false"
        >
          Отмена
        </v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :loading="loading"
          :disabled="!valid || loading"
          @click="runAnalysis"
        >
          Импортировать
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.dxf-import-card {
  border-radius: 8px;
  overflow: hidden;
}
</style>
