<template>
  <v-card>
    <v-toolbar color="brown" dark>
      <v-toolbar-title>Documents for Project</v-toolbar-title>
      <v-spacer></v-spacer>
      <v-chip v-if="projectId">{{ projectId }}</v-chip>
    </v-toolbar>

    <v-progress-linear :active="isLoading" indeterminate color="brown"></v-progress-linear>

    <v-list lines="two">
      <div v-if="!isLoading && documents.length === 0" class="text-center pa-4 grey--text">
        No documents found for this project.
      </div>
      <v-list-item
        v-for="doc in documents"
        :key="doc.id"
        :title="doc.fileName"
        :subtitle="`Description: ${doc.description || 'N/A'}`"
      >
        <template v-slot:append>
          <v-btn disabled icon="mdi-file-document-edit-outline" variant="text" title="Open in OnlyOffice (not implemented)"></v-btn>
        </template>
      </v-list-item>
    </v-list>
  </v-card>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useStore} from 'vuex';
import documentService from '@/services/document.service';
import type {Document, ProjectMultiline, ProjectPoint, ProjectPolygon} from '@/types/api';

const props = defineProps({
  projectId: {
    type: String,
    required: true,
  },
});

const store = useStore();

const documents = ref<Document[]>([]);
const isLoading = ref(false);

const geodataLoading = computed(() => store.state.geodata.isLoading);

// --- Наблюдатель за изменением projectId ---
watch(() => props.projectId, async (newProjectId) => {
  if (!newProjectId) {
    documents.value = [];
    return;
  }

  isLoading.value = true;
  documents.value = [];

  // 1. Получаем все гео-объекты для проекта
  await store.dispatch('geodata/fetchVectorDataForProject', newProjectId);
  
  const points = store.state.geodata.points as ProjectPoint[];
  const multilines = store.state.geodata.multilines as ProjectMultiline[];
  const polygons = store.state.geodata.polygons as ProjectPolygon[];

  const geoObjectIds = [
      ...points.map(p => p.id),
      ...multilines.map(m => m.id),
      ...polygons.map(p => p.id)
  ];

  // 2. Для каждого гео-объекта запрашиваем его документы
  const documentPromises = geoObjectIds.map(id => 
    documentService.getDocumentsForGeoObject(id)
  );

  try {
    const responses = await Promise.all(documentPromises);
    documents.value = responses.flatMap(response => response.data);
  } catch (error) {
    console.error("Error fetching documents for project:", error);
    // Здесь можно обработать ошибку, например, показать уведомление
  } finally {
    isLoading.value = false;
  }
}, { immediate: true }); // immediate: true, чтобы watcher сработал при инициализации

</script>
