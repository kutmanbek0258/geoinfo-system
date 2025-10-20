<template>
  <div id="onlyoffice-editor-container"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import documentService from '@/services/document.service';

let docEditor: any = null;
const route = useRoute();

const loadOnlyOfficeScript = () => {
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'http://localhost:8081/web-apps/apps/api/documents/api.js'; // URL вашего Document Server
    script.onload = () => resolve();
    script.onerror = (err) => reject(err);
    document.head.appendChild(script);
  });
};

onMounted(async () => {
  const documentId = route.params.id as string;
  if (!documentId) {
    console.error('No document ID found in route.');
    return;
  }

  try {
    await loadOnlyOfficeScript();
    const response = await documentService.getOnlyOfficeConfig(documentId, 'edit');
    const config = response.data;

    // URL is now absolute and correct from the backend.
    // No client-side manipulation is needed.

    // Добавляем контейнер в конфиг
    config.width = '100%';
    config.height = '100%';

    // @ts-ignore
    docEditor = new DocsAPI.DocEditor('onlyoffice-editor-container', config);

  } catch (error) {
    console.error('Error loading OnlyOffice editor:', error);
  }
});

onUnmounted(() => {
  if (docEditor) {
    docEditor.destroyEditor();
    docEditor = null;
  }
});

</script>

<style>
#onlyoffice-editor-container {
  width: 100vw;
  height: 100vh;
}
</style>
