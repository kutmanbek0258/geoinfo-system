<template>
  <v-card v-if="featureId" elevation="5">
    <v-card-title class="d-flex align-center">
      <v-icon left>mdi-map-marker</v-icon>
      <span class="ml-2">{{ featureName }}</span>
      <v-spacer></v-spacer>
      <v-btn icon="mdi-pencil" variant="text" @click="editFeature" title="Edit"></v-btn>
      <v-btn icon="mdi-delete" color="error" variant="text" @click="deleteFeature" title="Delete"></v-btn>
    </v-card-title>
    <v-card-subtitle>{{ featureDescription }}</v-card-subtitle>
    <v-divider></v-divider>

    <v-card-title class="d-flex align-center">
      <v-icon left>mdi-paperclip</v-icon>
      <span class="ml-2">Attached Documents</span>
    </v-card-title>
    <v-divider></v-divider>

    <v-card-text>
      <div class="d-flex justify-space-between align-center mb-3">
        <span class="text-subtitle-1">Object ID: {{ featureId.substring(0, 8) }}...</span>
        <div>
          <v-btn color="primary" @click="onUploadClick" :loading="isUploading">
            Upload File
          </v-btn>
        </div>
        <input type="file" ref="fileInput" @change="handleFileUpload" style="display: none" />
      </div>

      <v-progress-linear :active="isLoading" indeterminate color="primary"></v-progress-linear>

      <v-list v-if="documents.length > 0" lines="two">
        <v-list-item v-for="doc in documents" :key="doc.id">
          <template v-slot:prepend>
            <v-icon>{{ getFileIcon(doc.mimeType) }}</v-icon>
          </template>

          <v-list-item-title>{{ doc.fileName }}</v-list-item-title>
          <v-list-item-subtitle>{{ (doc.fileSizeBytes / 1024).toFixed(2) }} KB - {{ new Date(doc.uploadDate).toLocaleDateString() }}</v-list-item-subtitle>

          <template v-slot:append>
<!--            <v-btn icon="mdi-open-in-new" variant="text" @click="openDocument(doc)" title="Open in OnlyOffice"></v-btn>-->
            <v-btn icon="mdi-download" variant="text" @click="downloadDocument(doc)" title="Download"></v-btn>
            <v-btn icon="mdi-delete" color="error" variant="text" @click="deleteDocument(doc.id)" title="Delete"></v-btn>
          </template>
        </v-list-item>
      </v-list>

      <v-alert v-else-if="!isLoading" type="info" variant="tonal" class="mt-2">
        No documents attached to this object.
      </v-alert>

    </v-card-text>

    <!-- Диалог подтверждения загрузки -->
    <v-dialog v-model="uploadDialog" max-width="500px">
      <v-card>
        <v-card-title>Upload Document</v-card-title>
        <v-card-text>
          <p class="mb-4">File: <strong>{{ fileToUpload?.name }}</strong></p>
          <v-textarea v-model="uploadDescription" label="Description" rows="2"></v-textarea>
          <v-text-field v-model="uploadTags" label="Tags (comma-separated)"></v-text-field>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="cancelUpload">Cancel</v-btn>
          <v-btn color="primary" @click="confirmUpload" :loading="isUploading">Confirm & Upload</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Диалог редактирования -->
    <v-dialog v-model="editDialog" max-width="500px">
      <v-card>
        <v-card-title>Edit Geo Object</v-card-title>
        <v-card-text>
          <v-text-field v-model="featureToEdit.name" label="Name" required></v-text-field>
          <v-textarea v-model="featureToEdit.description" label="Description"></v-textarea>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="cancelEdit">Cancel</v-btn>
          <v-btn color="primary" @click="confirmEdit">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

  </v-card>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router'; // Импортируем useRouter
import type { Document } from '@/types/api';
import documentService from '@/services/document.service';

const props = defineProps({
  featureId: {
    type: String,
    default: null,
  },
  featureName: {
    type: String,
    default: 'Geo Object',
  },
  featureDescription: {
    type: String,
    default: 'No description available.',
  },
  featureType: {
    type: String,
    required: true,
  },
});

const store = useStore();
const router = useRouter();
const fileInput = ref<HTMLInputElement | null>(null);

// Состояние для диалога загрузки
const uploadDialog = ref(false);
const fileToUpload = ref<File | null>(null);
const uploadDescription = ref('');
const uploadTags = ref('');

// Состояние для диалога редактирования
const editDialog = ref(false);
const featureToEdit = ref<{ name: string, description: string }>({ name: '', description: '' });

// --- Получение данных из Vuex ---
const isLoading = computed(() => store.state.document?.isLoading || false);
const isUploading = computed(() => store.state.document?.isUploading || false);
const documents = computed<Document[]>(() => store.state.document?.documents || []);

// --- Наблюдатель за изменением ID ---
watch(() => props.featureId, (newId) => {
  store.dispatch('document/fetchDocumentsForObject', newId);
}, { immediate: true });

const editFeature = () => {
  featureToEdit.value = {
    name: props.featureName,
    description: props.featureDescription,
  };
  editDialog.value = true;
};

const cancelEdit = () => {
  editDialog.value = false;
};

const confirmEdit = () => {
  store.dispatch('geodata/updateFeature', {
    id: props.featureId,
    type: props.featureType,
    data: featureToEdit.value,
  });
  editDialog.value = false;
};

const deleteFeature = () => {
  if (confirm('Are you sure you want to delete this object?')) {
    store.dispatch('geodata/deleteFeature', {
      id: props.featureId,
      type: props.featureType,
    });
  }
};

// --- Методы ---
const openDocument = (doc: Document) => {
  router.push({ name: 'OnlyOfficeEditor', params: { id: doc.id } });
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
    console.error('Error downloading file:', error);
    alert('Failed to download file.');
  }
};

const deleteDocument = (docId: string) => {
  if (confirm('Are you sure you want to delete this document?')) {
    store.dispatch('document/deleteDocument', { docId, geoObjectId: props.featureId });
  }
};

const onUploadClick = () => {
  fileInput.value?.click();
};

const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (file) {
    fileToUpload.value = file;
    uploadDescription.value = '';
    uploadTags.value = '';
    uploadDialog.value = true;
  }
};

const cancelUpload = () => {
  uploadDialog.value = false;
  fileToUpload.value = null;
};

const confirmUpload = async () => {
  if (fileToUpload.value && props.featureId) {
    await store.dispatch('document/uploadDocument', {
      file: fileToUpload.value,
      geoObjectId: props.featureId,
      description: uploadDescription.value,
      tags: uploadTags.value,
    });
    // Сбрасываем состояние и закрываем диалог после завершения (успешного или нет)
    if (!isUploading.value) {
        uploadDialog.value = false;
        fileToUpload.value = null;
    }
  }
};

const getFileIcon = (mimeType: string) => {
    if (mimeType.includes('pdf')) return 'mdi-file-pdf-box';
    if (mimeType.includes('word')) return 'mdi-file-word-box';
    if (mimeType.includes('excel')) return 'mdi-file-excel-box';
    if (mimeType.includes('image')) return 'mdi-file-image';
    return 'mdi-file';
}

</script>
