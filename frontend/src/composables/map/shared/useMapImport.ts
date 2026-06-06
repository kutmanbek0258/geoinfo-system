import { ref } from 'vue';
import { useStore } from 'vuex';
import GeodataService from '@/services/geodata.service';

export function useMapImport(projectId: string) {
  const store = useStore();

  const importFileDialog = ref(false);
  const importFile = ref<File | null>(null);
  const isImporting = ref(false);

  const openImportFileDialog = () => {
    importFile.value = null;
    importFileDialog.value = true;
  };

  const executeFileImport = async (onSuccess?: () => void) => {
    if (!importFile.value || !projectId) return;

    isImporting.value = true;
    try {
      await GeodataService.importFileToProject(projectId, importFile.value);
      await store.dispatch('geodata/fetchVectorDataForProject', projectId);
      importFileDialog.value = false;
      if (onSuccess) onSuccess();
    } catch (error) {
      console.error("Import failed:", error);
    } finally {
      isImporting.value = false;
    }
  };

  return {
    importFileDialog,
    importFile,
    isImporting,
    openImportFileDialog,
    executeFileImport
  };
}
