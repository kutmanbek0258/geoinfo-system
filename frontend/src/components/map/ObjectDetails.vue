<template>
  <v-card v-if="featureId" elevation="5">
    <v-card-title class="d-flex align-center">
      <v-icon left>mdi-map-marker</v-icon>
      <span class="ml-2">{{ displayFeatureName }}</span>
      <v-spacer></v-spacer>
      <v-btn icon="mdi-pencil" variant="text" @click="editFeature" title="Edit"></v-btn>
      <v-btn icon="mdi-vector-selection" variant="text" @click="$emit('edit-geometry')" title="Edit Geometry"></v-btn>
      <v-btn icon="mdi-delete" color="error" variant="text" @click="deleteFeature" title="Delete"></v-btn>
      <v-btn icon="mdi-close" variant="text" @click="$emit('close')" title="Close"></v-btn>
    </v-card-title>
    <v-card-subtitle>{{ featureDescription }}</v-card-subtitle>
    <v-divider></v-divider>

    <v-img
        v-if="featureImageUrl"
        :src="featureImageUrl"
        height="200px"
        cover
        class="grey-lighten-2"
    >
      <template v-slot:placeholder>
        <v-row class="fill-height ma-0" align="center" justify="center">
          <v-progress-circular indeterminate color="grey-lighten-5"></v-progress-circular>
        </v-row>
      </template>
    </v-img>

    <v-card-title class="d-flex align-center">
      <v-icon left>mdi-paperclip</v-icon>
      <span class="ml-2">Attached Documents</span>
    </v-card-title>
    <v-divider></v-divider>

    <v-card-text>
      <div class="d-flex justify-space-between align-center mb-3">
        <span class="text-subtitle-1">Object ID: {{ featureId.substring(0, 8) }}...</span>
        <div>
          <v-btn color="secondary" @click="onUploadImageClick" class="mr-2" :loading="isCompressing">
            Upload Image
          </v-btn>
          <v-btn color="primary" @click="onUploadClick" :loading="isUploading">
            Upload File
          </v-btn>
        </div>
        <input type="file" ref="fileInput" @change="handleFileUpload" style="display: none" />
        <input type="file" ref="imageInput" @change="handleImageUpload" style="display: none" accept="image/*" />
      </div>

      <!-- Camera Details and Stream Controls -->
      <v-card v-if="isCamera" class="my-4" elevation="2">
        <v-card-title class="text-subtitle-1">Camera Details</v-card-title>
        <v-card-text>
          <p><strong>IP Address:</strong> {{ cameraDetails?.ip_address }}</p>
          <p><strong>Port:</strong> {{ cameraDetails?.port }}</p>
          <p><strong>Login:</strong> {{ cameraDetails?.login }}</p>
          <v-btn color="success" class="mt-3" @click="startStream" :loading="isLoading" :disabled="!!activeCameraStream">
            <v-icon left>mdi-play-circle</v-icon> Start Viewing
          </v-btn>
        </v-card-text>
      </v-card>

      <v-progress-linear :active="isLoading" indeterminate color="primary"></v-progress-linear>

      <v-list v-if="documents.length > 0" lines="two">
        <v-list-item v-for="doc in documents" :key="doc.id">
          <template v-slot:prepend>
            <v-icon>{{ getFileIcon(doc.mimeType) }}</v-icon>
          </template>

          <v-list-item-title>{{ doc.fileName }}</v-list-item-title>
          <v-list-item-subtitle>{{ (doc.fileSizeBytes / 1024).toFixed(2) }} KB - {{ new Date(doc.uploadDate).toLocaleDateString() }}</v-list-item-subtitle>

          <template v-slot:append>
            <v-btn icon="mdi-open-in-new" variant="text" @click="openDocument(doc)" title="Open in OnlyOffice"></v-btn>
            <v-btn icon="mdi-download" variant="text" @click="downloadDocument(doc)" title="Download"></v-btn>
            <v-btn icon="mdi-delete" color="error" variant="text" @click="deleteDocument(doc.id)" title="Delete"></v-btn>
          </template>
        </v-list-item>
      </v-list>

      <v-alert v-else-if="!isLoading" type="info" variant="tonal" class="mt-2">
        No documents attached to this object.
      </v-alert>

    </v-card-text>

    <!-- Stream Viewer Dialog -->
    <v-dialog v-model="showStreamModal" fullscreen :scrim="false" transition="dialog-bottom-transition" @update:modelValue="onStreamDialogClose">
      <v-card>
        <v-toolbar dark color="primary">
          <v-btn icon dark @click="stopStream">
            <v-icon>mdi-close</v-icon>
          </v-btn>
          <v-toolbar-title>Camera Stream: {{ featureName }}</v-toolbar-title>
        </v-toolbar>
        <WebRtcPlayer
            v-if="activeCameraStream?.webRtcUrl"
            :stream-url="activeCameraStream.webRtcUrl"
            @close="stopStream"
        />
      </v-card>
    </v-dialog>


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
          <v-btn variant="text" @click="cancelUpload">Cancel</v-btn>
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

          <!-- Display Point Type -->
          <v-text-field
            v-if="featureType === 'Point' && featureToEdit.characteristics?.type"
            :model-value="featureToEdit.characteristics.type"
            label="Point Type"
            readonly
            variant="outlined"
            class="mt-4"
          ></v-text-field>

          <!-- Camera Details (conditional and editable for camera type points) -->
          <template v-if="isCamera">
            <v-divider class="my-4"></v-divider>
            <v-subheader>Camera Configuration</v-subheader>
            <v-text-field v-model="cameraEditDetails.ip_address" label="Camera IP Address" required></v-text-field>
            <v-text-field v-model="cameraEditDetails.port" label="Camera Port" type="number" required></v-text-field>
            <v-text-field v-model="cameraEditDetails.login" label="Camera Login" required></v-text-field>
            <v-text-field v-model="cameraEditDetails.password" label="Camera Password" type="password" required></v-text-field>
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="cancelEdit">Cancel</v-btn>
          <v-btn color="primary" @click="confirmEdit">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

  </v-card>
</template>

<script setup lang="ts">
import { ref, watch, computed, onUnmounted } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import imageCompression from 'browser-image-compression';
import type { Document, ProjectPoint } from '@/types/api';
import documentService from '@/services/document.service';
import WebRtcPlayer from '@/components/webrtc/WebRtcPlayer.vue';

const videoEl = ref<HTMLVideoElement | null>(null)
const pc = ref<RTCPeerConnection | null>(null)
const isWebRtcLoading = ref(false)

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
  featureImageUrl: {
    type: String,
    default: null,
  },
  // Added to get full feature data including characteristics
  fullFeatureData: {
    type: Object as () => ProjectPoint | null,
    default: null,
  }
});

const emit = defineEmits(['close', 'edit-geometry']);

const store = useStore();
const router = useRouter();
const fileInput = ref<HTMLInputElement | null>(null);
const imageInput = ref<HTMLInputElement | null>(null); // For main image

// State for camera streaming
const showStreamModal = ref(false);
const streamUrl = ref('');

// --- Camera Streaming Logic ---
const isCamera = computed(() => {
  return props.featureType === 'Point' && props.fullFeatureData?.characteristics?.type === 'camera';
});

const cameraDetails = computed(() => {
  if (isCamera.value && props.fullFeatureData?.characteristics) {
    return {
      ip_address: props.fullFeatureData.characteristics.ip_address,
      port: props.fullFeatureData.characteristics.port,
      login: props.fullFeatureData.characteristics.login,
      password: props.fullFeatureData.characteristics.password,
    };
  }
  return null;
});

const activeCameraStream = computed(() => store.state.geodata.activeCameraStream);

const startStream = async () => {
  if (!props.featureId) return

  isWebRtcLoading.value = true

  await store.dispatch('geodata/startCameraStream', props.featureId)

  const stream = activeCameraStream.value
  if (!stream) return

  showStreamModal.value = true
}

const stopStream = async () => {
  if (pc.value) {
    pc.value.close()
    pc.value = null
  }

  if (videoEl.value) {
    videoEl.value.srcObject = null
  }

  await store.dispatch('geodata/stopCameraStream', props.featureId)
  showStreamModal.value = false
}

const onStreamDialogClose = (isOpen: boolean) => {
  if (!isOpen && activeCameraStream.value) {
    stopStream();
  }
}

// --- Lifecycle Hook to stop stream if component unmounts ---
onUnmounted(() => {
  if (props.featureId && activeCameraStream.value && activeCameraStream.value.geoObjectId === props.featureId) {
    stopStream();
  }
});

// Состояние для диалога загрузки
const uploadDialog = ref(false);
const fileToUpload = ref<File | null>(null);
const uploadDescription = ref('');
const uploadTags = ref('');
const isCompressing = ref(false);

// Состояние для диалога редактирования
const editDialog = ref(false);
const featureToEdit = ref<{
  name: string,
  description: string,
  type: string, // Add type
  characteristics: Record<string, any> // Add characteristics
}>({ name: '', description: '', type: '', characteristics: {} });

const cameraEditDetails = ref({ // Для временного хранения данных камеры при редактировании
  ip_address: '',
  port: 8000,
  login: '',
  password: '',
});

// --- Вычисляемое свойство для отображения названия фичи ---
const displayFeatureName = computed<string>(() => {
  const name = props.featureName;
  if (typeof name === 'string' && name.length > 10) {
    return `${name.substring(0, 10)}...`;
  }
  return name || 'Geo Object';
});

// --- Получение данных из Vuex ---
const isLoading = computed(() => store.state.document?.isLoading || false);
const isUploading = computed(() => store.state.document?.isUploading || false);
const documents = computed<Document[]>(() => store.state.document?.documents || []);

// --- Наблюдатель за изменением ID ---
watch(() => props.featureId, (newId) => {
  if (newId) {
    store.dispatch('document/fetchDocumentsForObject', newId);
  }
}, { immediate: true });

const editFeature = () => {
  featureToEdit.value = {
    name: props.featureName,
    description: props.featureDescription,
    type: props.featureType, // Assume featureType is the main type (Point, MultiLineString, Polygon)
    characteristics: props.fullFeatureData?.characteristics || {},
  };

  // If it's a camera, pre-fill cameraEditDetails
  if (isCamera.value && props.fullFeatureData?.characteristics) {
    cameraEditDetails.value = {
      ip_address: props.fullFeatureData.characteristics.ip_address || '',
      port: props.fullFeatureData.characteristics.port || 8000,
      login: props.fullFeatureData.characteristics.login || '',
      password: props.fullFeatureData.characteristics.password || '',
    };
  } else {
    // Reset if not a camera
    cameraEditDetails.value = { ip_address: '', port: 8000, login: '', password: '' };
  }
  editDialog.value = true;
};

const cancelEdit = () => {
  editDialog.value = false;
};

const confirmEdit = () => {
  const sanitizeString = (str: string) => {
    if (typeof str !== 'string') return str;
    return str.replace(/^"|"$/g, '').replace(/\\"/g, '"');
  };
  // Construct updated characteristics
  let updatedCharacteristics = { ...featureToEdit.value.characteristics };
  if (isCamera.value) { // Use isCamera to ensure we only update camera fields for camera points
    updatedCharacteristics = { ...updatedCharacteristics, ...cameraEditDetails.value };
  } else {
      // If it was a camera and now being edited, ensure the 'type' in characteristics is maintained or reset
      // For now, we assume the type is part of characteristics from backend
      // and we just update camera details if it's a camera
  }


  store.dispatch('geodata/updateFeature', {
    id: props.featureId,
    type: props.featureType, // This is 'Point', 'MultiLineString', 'Polygon'
    data: {
      name: featureToEdit.value.name,
      description: featureToEdit.value.description,
      characteristics: updatedCharacteristics,
    },
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

const onUploadImageClick = () => {
  imageInput.value?.click();
};

const handleImageUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file || !props.featureId) return;

  const options = {
    maxSizeMB: 1, // Максимальный размер файла в мегабайтах
    maxWidthOrHeight: 1920, // Максимальная ширина или высота
    useWebWorker: true, // Использовать Web Worker для производительности
  };

  isCompressing.value = true;
  try {
    console.log(`Original file size: ${(file.size / 1024 / 1024).toFixed(2)} MB`);
    const compressedFile = await imageCompression(file, options);
    console.log(`Compressed file size: ${(compressedFile.size / 1024 / 1024).toFixed(2)} MB`);

    let objectType: 'points' | 'multilines' | 'polygons';
    if (props.featureType === 'Point') {
        objectType = 'points';
    } else if (props.featureType === 'MultiLineString') {
        objectType = 'multilines';
    } else if (props.featureType === 'Polygon') {
        objectType = 'polygons';
    } else {
        return; // Or handle error
    }

    await store.dispatch('geodata/uploadMainImage', {
      objectType: objectType,
      objectId: props.featureId,
      file: compressedFile, // Отправляем сжатый файл
    });

  } catch (error) {
    console.error('Error during image compression:', error);
    // Можно показать ошибку пользователю
  } finally {
    isCompressing.value = false;
    // Очищаем инпут, чтобы можно было выбрать тот же файл снова
    target.value = '';
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
