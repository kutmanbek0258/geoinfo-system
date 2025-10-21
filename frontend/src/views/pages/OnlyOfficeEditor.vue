<template>
  <div id="onlyoffice-editor-container"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import documentService from '@/services/document.service';

let docEditor: any = null;
const route = useRoute();

const SCRIPT_ID = 'onlyoffice-api-script';

const loadOnlyOfficeScript = () => {
  return new Promise<void>((resolve, reject) => {
    if (document.getElementById(SCRIPT_ID)) {
        return resolve();
    }
    const script = document.createElement('script');
    script.id = SCRIPT_ID;
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
    console.log(config);

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
  console.log('Unmounting OnlyOfficeEditor...');
  if (docEditor) {
    console.log('Destroying editor instance.');
    docEditor.destroyEditor();
    docEditor = null;
  } else {
    console.log('No editor instance to destroy.');
  }

  setTimeout(() => {
    const script = document.getElementById(SCRIPT_ID);
    if (script) {
      console.log('Removing OnlyOffice API script after a short delay.');
      script.remove();
    } else {
      console.log('OnlyOffice API script not found in DOM, cannot remove.');
    }
  }, 100);
});

</script>

<style>
#onlyoffice-editor-container {
  width: 100vw;
  height: 100vh;
}
</style>
