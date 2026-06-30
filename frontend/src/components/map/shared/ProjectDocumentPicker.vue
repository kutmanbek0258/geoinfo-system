<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useStore } from 'vuex';
import type { Document } from '@/types/api';

const props = defineProps<{
  modelValue: any;
  title: string;
  allowedExtensions?: string[];
}>();

const emit = defineEmits(['update:modelValue']);

const store = useStore();
const selectedDoc = ref<Document | null>(null);

const projectId = computed(() => store.state.geodata.selectedProjectId);
const documents = computed(() => store.state.geodata.documents || []);

// Фильтрация документов по разрешенным расширениям
const filteredDocuments = computed(() => {
  if (!props.allowedExtensions || props.allowedExtensions.length === 0) {
    return documents.value;
  }
  return documents.value.filter((doc: Document) => {
    const fileNameLower = doc.fileName.toLowerCase();
    return props.allowedExtensions!.some(ext => fileNameLower.endsWith(ext.toLowerCase()));
  });
});

onMounted(() => {
  if (projectId.value) {
    store.dispatch('geodata/fetchDocuments', projectId.value);
  }
});

watch(projectId, (newVal) => {
  if (newVal) {
    store.dispatch('geodata/fetchDocuments', newVal);
  }
});

// Если modelValue сброшен извне
watch(() => props.modelValue, (newVal) => {
  if (!newVal) {
    selectedDoc.value = null;
  }
});

// Отслеживание выбора файла и обновление modelValue
watch(selectedDoc, (newDoc) => {
  if (newDoc) {
    emit('update:modelValue', {
      type: 'DIRECT_S3',
      s3Url: `s3://documents/${newDoc.minioObjectKey}`
    });
  } else {
    emit('update:modelValue', null);
  }
});
</script>

<template>
  <div class="project-document-picker mb-3">
    <v-select
      v-model="selectedDoc"
      :items="filteredDocuments"
      item-title="fileName"
      return-object
      :label="title"
      placeholder="Выберите файл проекта"
      prepend-inner-icon="mdi-file-cabinet"
      variant="outlined"
      density="comfortable"
      :rules="[v => !!v || 'Необходимо выбрать файл']"
      :no-data-text="`Файлы c расширением ${allowedExtensions?.join(', ') || ''} не найдены`"
    >
      <template v-slot:item="{ props: itemProps, item }">
        <v-list-item
          v-bind="itemProps"
          :subtitle="(item.raw as any).description || 'Без описания'"
        ></v-list-item>
      </template>
    </v-select>
  </div>
</template>
