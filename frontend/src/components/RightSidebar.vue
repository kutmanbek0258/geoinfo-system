<template>
  <v-sheet class="d-flex flex-column pa-4" height="100%">
    <div v-if="!feature">
      <v-card-title>No object selected</v-card-title>
      <v-card-text>Click an object on the map or in the list to see its details.</v-card-text>
    </div>

    <div v-else>
      <!-- Feature Details -->
      <v-text-field label="Name" v-model="feature.name" variant="outlined" density="compact"></v-text-field>
      <v-textarea label="Description" v-model="feature.description" variant="outlined" class="mt-4"></v-textarea>

      <v-divider class="my-4"></v-divider>

      <!-- Documents List -->
      <h4 class="mb-2">Documents ({{ documents.length }})</h4>
      <v-progress-circular v-if="isLoadingDocs" indeterminate color="primary"></v-progress-circular>
      <v-list v-else density="compact">
        <v-list-item
          v-for="doc in documents"
          :key="doc.id"
          :title="doc.fileName"
          :subtitle="`${(doc.fileSizeBytes / 1024).toFixed(1)} KB`"
        >
          <template v-slot:append>
            <v-btn icon="mdi-pencil" variant="text" size="small" @click="editDocument(doc.id)"></v-btn>
            <v-btn icon="mdi-delete" variant="text" size="small" @click="deleteDocument(doc.id)"></v-btn>
          </template>
        </v-list-item>
      </v-list>

      <v-file-input
        label="Upload new document"
        class="mt-4"
        variant="outlined"
        density="compact"
        hide-details
        @change="handleFileUpload"
      ></v-file-input>

      <v-spacer></v-spacer>
      <v-btn block color="primary" class="mt-6">Save Changes</v-btn>
    </div>
  </v-sheet>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue';
import { useStore } from 'vuex';

const props = defineProps<{
  feature: any | null;
}>();

const store = useStore();

// --- Vuex State ---
const documents = computed(() => store.getters['document/getDocuments']);
const isLoadingDocs = computed(() => store.getters['document/isLoading']);

// --- Watchers ---
watch(() => props.feature, (newFeature) => {
  if (newFeature && newFeature.id) {
    store.dispatch('document/fetchAll', { geoObjectId: newFeature.id });
  }
});

// --- Methods ---
const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (file && props.feature?.id) {
    store.dispatch('document/create', {
      file,
      geoObjectId: props.feature.id,
      description: file.name,
    });
  }
};

const deleteDocument = (docId: string) => {
  if (props.feature?.id) {
    store.dispatch('document/delete', { id: docId, geoObjectId: props.feature.id });
  }
};

const editDocument = (docId: string) => {
    // Открываем only_office.html в новой вкладке
    const url = `/only_office.html?docId=${docId}&mode=edit`;
    window.open(url, '_blank');
};
</script>
