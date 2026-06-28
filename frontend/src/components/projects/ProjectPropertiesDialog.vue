<template>
  <v-dialog v-model="visible" max-width="700px" scrollable>
    <v-card class="project-properties-card">
      <v-card-title class="d-flex align-center bg-primary text-white py-4 px-6">
        <v-icon left class="mr-2">mdi-information-outline</v-icon>
        <span class="text-h6 font-weight-bold">Свойства проекта</span>
        <v-spacer></v-spacer>
        <v-btn icon="mdi-close" variant="text" color="white" @click="visible = false"></v-btn>
      </v-card-title>

      <v-divider></v-divider>

      <v-card-text class="pa-6" style="max-height: 70vh;">
        <div v-if="project">
          <!-- 1. Секция Обложки проекта -->
          <div class="mb-6">
            <div class="text-subtitle-1 font-weight-bold mb-2 d-flex align-center">
              <v-icon class="mr-2" color="primary">mdi-image</v-icon>
              Обложка проекта
            </div>
            
            <div class="position-relative cover-image-container rounded-lg overflow-hidden border">
              <v-img
                v-if="coverImageUrl"
                :src="coverImageUrl"
                height="220px"
                cover
                class="bg-grey-lighten-2"
              >
                <template v-slot:placeholder>
                  <v-row class="fill-height ma-0" align="center" justify="center">
                    <v-progress-circular indeterminate color="primary"></v-progress-circular>
                  </v-row>
                </template>
              </v-img>
              <div v-else class="d-flex flex-column align-center justify-center bg-grey-lighten-4" style="height: 220px;">
                <v-icon size="48" color="grey-lighten-1" class="mb-2">mdi-image-off</v-icon>
                <span class="text-body-2 text-grey-darken-1">Обложка не установлена</span>
              </div>
              
              <!-- Кнопки управления поверх изображения -->
              <div class="cover-image-actions d-flex justify-center align-center">
                <v-btn
                  color="white"
                  variant="elevated"
                  prepend-icon="mdi-camera"
                  class="mr-2"
                  @click="triggerCoverSelect"
                  :loading="isCompressingCover"
                >
                  {{ coverImageUrl ? 'Изменить' : 'Загрузить' }}
                </v-btn>
                <v-btn
                  v-if="coverImageUrl && coverDocumentId"
                  color="error"
                  variant="elevated"
                  icon="mdi-delete"
                  @click="deleteCover"
                  :loading="isDeletingCover"
                  title="Удалить обложку"
                ></v-btn>
              </div>
            </div>
            <input type="file" ref="coverInput" @change="handleCoverUpload" style="display: none" accept="image/*" />
          </div>

          <!-- 2. Секция описания и BBox -->
          <v-row class="mb-4">
            <v-col cols="12" md="6" class="py-1">
              <div class="text-body-2 text-grey-darken-1">Название</div>
              <div class="text-body-1 font-weight-medium mb-3">{{ project.name }}</div>
            </v-col>
            <v-col cols="12" md="6" class="py-1">
              <div class="text-body-2 text-grey-darken-1">Период выполнения</div>
              <div class="text-body-1 font-weight-medium mb-3">
                {{ formatDate(project.startDate) }} — {{ formatDate(project.endDate) }}
              </div>
            </v-col>
            <v-col cols="12" class="py-1">
              <div class="text-body-2 text-grey-darken-1">Описание</div>
              <div class="text-body-1 mb-3 text-justify">{{ project.description || 'Описание отсутствует.' }}</div>
            </v-col>
            <v-col cols="12" class="py-1">
              <div class="text-body-2 text-grey-darken-1">Географические границы (BBox)</div>
              <div class="text-body-2 font-weight-bold font-mono pa-2 bg-grey-lighten-4 rounded border mt-1">
                <v-icon size="small" class="mr-1" color="primary">mdi-vector-rectangle</v-icon>
                {{ formatBbox(project.bbox) }}
              </div>
            </v-col>
          </v-row>

          <v-divider class="my-6"></v-divider>

          <!-- 3. Секция прикрепленных файлов -->
          <div class="mb-4">
            <div class="d-flex justify-space-between align-center mb-3">
              <div class="text-subtitle-1 font-weight-bold d-flex align-center">
                <v-icon class="mr-2" color="primary">mdi-paperclip</v-icon>
                Документы проекта ({{ otherDocuments.length }})
              </div>
              <v-btn
                color="primary"
                prepend-icon="mdi-plus"
                variant="outlined"
                size="small"
                @click="showUploadForm = !showUploadForm"
              >
                {{ showUploadForm ? 'Скрыть форму' : 'Добавить файл' }}
              </v-btn>
            </div>

            <!-- Форма добавления файла -->
            <v-expand-transition>
              <div v-if="showUploadForm" class="pa-4 bg-grey-lighten-4 rounded-lg border mb-4">
                <div class="text-subtitle-2 font-weight-bold mb-3">Загрузка нового документа</div>
                <v-file-input
                  v-model="fileToUpload"
                  label="Выберите файл"
                  prepend-icon="mdi-file-outline"
                  density="compact"
                  variant="outlined"
                  hide-details
                  class="mb-3"
                ></v-file-input>
                <v-text-field
                  v-model="uploadDescription"
                  label="Описание файла"
                  density="compact"
                  variant="outlined"
                  hide-details
                  class="mb-3"
                ></v-text-field>
                <v-text-field
                  v-model="uploadTags"
                  label="Теги (через запятую)"
                  density="compact"
                  variant="outlined"
                  hide-details
                  placeholder="отчет, вектор, архив"
                  class="mb-4"
                ></v-text-field>
                <div class="d-flex justify-end">
                  <v-btn color="grey-darken-1" variant="text" size="small" class="mr-2" @click="cancelUpload">Отмена</v-btn>
                  <v-btn color="primary" size="small" @click="uploadProjectDocument" :loading="isUploading" :disabled="!fileToUpload">
                    Загрузить
                  </v-btn>
                </div>
              </div>
            </v-expand-transition>

            <!-- Список документов -->
            <v-progress-linear v-if="isLoadingDocs" indeterminate color="primary" class="mb-2"></v-progress-linear>

            <v-list v-if="otherDocuments.length > 0" lines="two" class="border rounded-lg pa-0 overflow-hidden">
              <template v-for="(doc, idx) in otherDocuments" :key="doc.id">
                <v-list-item class="px-4 py-2">
                  <template v-slot:prepend>
                    <v-avatar color="primary-lighten-5" rounded="0" class="mr-2">
                      <v-icon color="primary" size="large">{{ getFileIcon(doc.mimeType) }}</v-icon>
                    </v-avatar>
                  </template>

                  <v-list-item-title class="font-weight-medium">{{ doc.fileName }}</v-list-item-title>
                  <v-list-item-subtitle class="text-caption mt-1">
                    {{ (doc.fileSizeBytes / 1024).toFixed(1) }} КБ | {{ formatDate(doc.uploadDate) }}
                    <span v-if="doc.description" class="d-block text-grey-darken-2 mt-1">
                      {{ doc.description }}
                    </span>
                    <span v-if="doc.tags && doc.tags.length > 0" class="d-block mt-1">
                      <v-chip v-for="t in doc.tags" :key="t.id" size="x-small" color="primary" variant="outlined" class="mr-1">
                        {{ t.name }}
                      </v-chip>
                    </span>
                  </v-list-item-subtitle>

                  <template v-slot:append>
                    <v-btn icon="mdi-open-in-new" variant="text" color="primary" size="small" @click="openInOnlyOffice(doc)" title="Открыть в OnlyOffice"></v-btn>
                    <v-btn icon="mdi-download" variant="text" color="success" size="small" @click="downloadDocument(doc)" title="Скачать"></v-btn>
                    <v-btn icon="mdi-delete" variant="text" color="error" size="small" @click="deleteDocument(doc.id)" title="Удалить"></v-btn>
                  </template>
                </v-list-item>
                <v-divider v-if="idx < otherDocuments.length - 1"></v-divider>
              </template>
            </v-list>
            <div v-else-if="!isLoadingDocs" class="text-center py-6 text-grey-darken-1 border rounded-lg bg-grey-lighten-5">
              Нет прикрепленных документов
            </div>
          </div>
        </div>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { Project, Document } from '@/types/api';
import documentService from '@/services/document.service';
import imageCompression from 'browser-image-compression';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';

const props = defineProps<{
  modelValue: boolean;
  project: Project | null;
}>();

const emit = defineEmits(['update:modelValue']);
const router = useRouter();
const store = useStore();

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const documents = computed<Document[]>(() => store.state.geodata.documents || []);
const isLoadingDocs = computed(() => store.state.geodata.isLoading || false);
const showUploadForm = ref(false);

// Upload Cover state
const coverInput = ref<HTMLInputElement | null>(null);
const isCompressingCover = ref(false);
const isDeletingCover = ref(false);

// Upload File state
const fileToUpload = ref<File | null>(null);
const uploadDescription = ref('');
const uploadTags = ref('');
const isUploading = ref(false);

// Filter documents
const coverDocument = computed(() => {
  return documents.value.find(doc => doc.tags && doc.tags.some(t => t.name === 'cover'));
});

const coverDocumentId = computed(() => coverDocument.value?.id || null);

const coverImageUrl = computed(() => {
  if (coverDocumentId.value) {
    return `/api/documents/public/image/${coverDocumentId.value}`;
  }
  return null;
});

const otherDocuments = computed(() => {
  return documents.value.filter(doc => !doc.tags || !doc.tags.some(t => t.name === 'cover'));
});

// Load project documents
const loadDocuments = () => {
  if (!props.project?.id) return;
  store.dispatch('geodata/fetchDocuments', props.project.id);
};

watch(() => props.project?.id, (newId) => {
  showUploadForm.value = false;
  cancelUpload();
  if (newId) {
    loadDocuments();
  }
}, { immediate: true });

// Format helper functions
const formatDate = (dateStr: any) => {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString();
};

const formatBbox = (geom: any) => {
  if (!geom || !geom.coordinates) return 'Нет геоданных (пустой bbox)';
  try {
    let coords: [number, number][] = [];
    if (geom.type === 'Polygon') {
      coords = geom.coordinates[0];
    } else if (geom.type === 'MultiPolygon') {
      coords = geom.coordinates[0][0];
    } else {
      return JSON.stringify(geom);
    }
    if (!coords || coords.length === 0) return 'Пустой bbox';
    
    const lons = coords.map(c => c[0]);
    const lats = coords.map(c => c[1]);
    const minLon = Math.min(...lons).toFixed(6);
    const maxLon = Math.max(...lons).toFixed(6);
    const minLat = Math.min(...lats).toFixed(6);
    const maxLat = Math.max(...lats).toFixed(6);
    
    return `ЮЗ: [${minLat}, ${minLon}] | СВ: [${maxLat}, ${maxLon}]`;
  } catch (e) {
    console.error('Error formatting bbox:', e);
    return 'Некорректный формат геометрии';
  }
};

const getFileIcon = (mimeType: string) => {
  if (!mimeType) return 'mdi-file';
  if (mimeType.includes('pdf')) return 'mdi-file-pdf-box';
  if (mimeType.includes('word') || mimeType.includes('officedocument.word')) return 'mdi-file-word-box';
  if (mimeType.includes('excel') || mimeType.includes('officedocument.sheet')) return 'mdi-file-excel-box';
  if (mimeType.includes('image')) return 'mdi-file-image';
  return 'mdi-file';
};

// Cover Image actions
const triggerCoverSelect = () => {
  coverInput.value?.click();
};

const handleCoverUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file || !props.project?.id) return;

  const options = {
    maxSizeMB: 1,
    maxWidthOrHeight: 1920,
    useWebWorker: true,
  };

  isCompressingCover.value = true;
  try {
    const compressedFile = await imageCompression(file, options);
    
    // Delete old cover first if it exists
    if (coverDocumentId.value) {
      await store.dispatch('geodata/deleteProjectDocument', {
        docId: coverDocumentId.value,
        projectId: props.project.id
      });
    }
    
    await store.dispatch('geodata/uploadProjectDocument', {
      file: compressedFile,
      projectId: props.project.id,
      description: 'Обложка проекта',
      tags: 'cover'
    });
  } catch (err) {
    console.error('Failed to upload cover:', err);
    alert('Не удалось загрузить обложку.');
  } finally {
    isCompressingCover.value = false;
    target.value = '';
  }
};

const deleteCover = async () => {
  if (!coverDocumentId.value || !props.project?.id) return;
  if (!confirm('Вы уверены, что хотите удалить обложку проекта?')) return;
  
  isDeletingCover.value = true;
  try {
    await store.dispatch('geodata/deleteProjectDocument', {
      docId: coverDocumentId.value,
      projectId: props.project.id
    });
  } catch (err) {
    console.error('Failed to delete cover:', err);
    alert('Не удалось удалить обложку.');
  } finally {
    isDeletingCover.value = false;
  }
};

// File Upload actions
const cancelUpload = () => {
  fileToUpload.value = null;
  uploadDescription.value = '';
  uploadTags.value = '';
};

const uploadProjectDocument = async () => {
  if (!fileToUpload.value || !props.project?.id) return;
  
  isUploading.value = true;
  try {
    await store.dispatch('geodata/uploadProjectDocument', {
      file: fileToUpload.value,
      projectId: props.project.id,
      description: uploadDescription.value,
      tags: uploadTags.value
    });
    cancelUpload();
    showUploadForm.value = false;
  } catch (err) {
    console.error('Failed to upload document:', err);
    alert('Не удалось загрузить документ.');
  } finally {
    isUploading.value = false;
  }
};

const openInOnlyOffice = (doc: Document) => {
  const routeData = router.resolve({ name: 'OnlyOfficeEditor', params: { id: doc.id } });
  window.open(routeData.href, '_blank');
};

const downloadDocument = async (doc: Document) => {
  try {
    const response = await documentService.downloadDocument(doc.id);
    const blob = new Blob([response.data], { type: response.headers['content-type'] });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.setAttribute('download', doc.fileName);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
  } catch (error) {
    console.error('Error downloading document:', error);
    alert('Не удалось скачать файл.');
  }
};

const deleteDocument = async (docId: string) => {
  if (!props.project?.id) return;
  if (!confirm('Вы уверены, что хотите удалить этот документ?')) return;
  try {
    await store.dispatch('geodata/deleteProjectDocument', {
      docId,
      projectId: props.project.id
    });
  } catch (err) {
    console.error('Failed to delete document:', err);
    alert('Не удалось удалить файл.');
  }
};
</script>

<style scoped>
.cover-image-container {
  border: 1px solid rgba(0, 0, 0, 0.12);
  transition: all 0.3s ease;
  position: relative;
}

.cover-image-container:hover .cover-image-actions {
  opacity: 1;
}

.cover-image-actions {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.font-mono {
  font-family: monospace;
}
</style>
