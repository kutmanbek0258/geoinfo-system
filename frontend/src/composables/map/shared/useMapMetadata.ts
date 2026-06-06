import { ref } from 'vue';
import { useStore } from 'vuex';
import type { Status } from '@/types/api';
import { ensureMultiType } from '@/util/geo.util';

export function useMapMetadata(projectId: string) {
  const store = useStore();

  const metadataDialog = ref(false);
  const drawingType = ref('');
  const newObjectGeometry = ref<any>(null);
  const pointTypes = ['camera', 'pillar', 'other'];

  const newObjectMetadata = ref({
    name: '',
    description: '',
    status: 'IN_PROCESS' as Status,
    type: 'other',
    characteristics: {} as Record<string, any>
  });

  const newObjectCameraDetails = ref({
    ip_address: '',
    port: 554,
    login: '',
    password: '',
  });

  const cancelNewFeature = () => {
    metadataDialog.value = false;
    newObjectMetadata.value = {
      name: '',
      description: '',
      status: 'IN_PROCESS' as Status,
      type: 'other',
      characteristics: {}
    };
    newObjectCameraDetails.value = { ip_address: '', port: 8000, login: '', password: '' };
  };

  const saveNewFeature = async (onSuccess?: () => void) => {
    if (!newObjectGeometry.value || !projectId) return;

    let characteristics = { type: newObjectMetadata.value.type };
    if (newObjectMetadata.value.type === 'camera') {
      characteristics = { ...characteristics, ...newObjectCameraDetails.value };
    }

    const payload = {
      projectId: projectId,
      folderId: store.state.geodata.selectedFolderId,
      geom: ensureMultiType(newObjectGeometry.value),
      name: newObjectMetadata.value.name,
      description: newObjectMetadata.value.description,
      status: newObjectMetadata.value.status,
      characteristics: characteristics,
    };

    await store.dispatch('geodata/createFeature', { type: drawingType.value, data: payload });

    metadataDialog.value = false;
    cancelNewFeature();
    if (onSuccess) onSuccess();
  };

  return {
    metadataDialog,
    drawingType,
    newObjectGeometry,
    pointTypes,
    newObjectMetadata,
    newObjectCameraDetails,
    cancelNewFeature,
    saveNewFeature
  };
}
