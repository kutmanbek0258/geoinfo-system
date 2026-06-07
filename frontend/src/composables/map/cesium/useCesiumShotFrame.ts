import { ref, type Ref } from 'vue';
import { useStore } from 'vuex';
import { debounce } from 'lodash';
import * as Cesium from 'cesium';
import GeodataService from '@/services/geodata.service';

export function useCesiumShotFrame(
  viewer: Ref<Cesium.Viewer | null>,
  projectId: Ref<string>,
  selectedFeatureId: Ref<string | null>,
  selectedFeature: Ref<any | null>,
  isGeometryEditMode: Ref<boolean>,
  createEntitiesFromGeoJSON: (v: Cesium.Viewer, geom: any, options: any) => Cesium.Entity[],
  sampleHeights: (points: Cesium.Cartesian3[]) => Promise<number[][]>
) {
  const store = useStore();
  const isZoomHighEnough = ref(false);
  const modifiedSubIds = ref(new Set<number>());
  const isLoadingParts = ref(false);

  const editPoints = ref<{subId: number, points: Cesium.Cartesian3[]}[]>([]);

  const fetchParts = debounce(async () => {
    const v = viewer.value;
    if (!isGeometryEditMode.value || !isZoomHighEnough.value || !selectedFeatureId.value || !selectedFeature.value || !v) return;

    const rect = v.camera.computeViewRectangle();
    if (!rect) return;

    const typeMap: Record<string, 'points' | 'multilines' | 'polygons'> = {
      'Point': 'points',
      'MultiLineString': 'multilines',
      'Polygon': 'polygons'
    };
    const apiType = typeMap[selectedFeature.value.type];
    if (!apiType) return;

    isLoadingParts.value = true;
    try {
      const response = await GeodataService.getGeometryParts(
        apiType, selectedFeatureId.value,
        Cesium.Math.toDegrees(rect.west), Cesium.Math.toDegrees(rect.south),
        Cesium.Math.toDegrees(rect.east), Cesium.Math.toDegrees(rect.north)
      );
      
      response.data.forEach((part: any) => {
        const partId = `edit_${selectedFeatureId.value}_${part.subId}`;
        if (v.entities.getById(partId)) return;

        createEntitiesFromGeoJSON(v, JSON.parse(part.geojson), {
          id: partId, name: `${selectedFeature.value?.name} (Part ${part.subId})`,
          sub_id: part.subId, show: true, style: selectedFeature.value?.characteristics?.style
        });
      });
    } catch (err) {
      console.error("Failed to fetch geometry parts:", err);
    } finally {
      isLoadingParts.value = false;
    }
  }, 1000);

  const handleCameraMoveForShotFrame = () => {
    const v = viewer.value;
    if (!isGeometryEditMode.value || !v) return;
    isZoomHighEnough.value = v.camera.positionCartographic.height < 5000;
    if (isZoomHighEnough.value) fetchParts();
  };

  const enterGeometryEditMode = (refreshMvt: () => void) => {
    const v = viewer.value;
    if (!v || !selectedFeature.value) return;

    isZoomHighEnough.value = v.camera.positionCartographic.height < 5000;
    if (!isZoomHighEnough.value) {
      alert(`Please zoom in closer to edit this complex object in 3D.`);
      return;
    }

    isGeometryEditMode.value = true;
    editPoints.value = [];
    modifiedSubIds.value.clear();
    refreshMvt();

    v.camera.moveEnd.addEventListener(handleCameraMoveForShotFrame);
    fetchParts();
  };

  const exitEditMode = (refreshMvt: () => void) => {
    const v = viewer.value;
    if (!v) return;
    v.entities.values.filter(e => e.id.startsWith('edit_')).forEach(e => v.entities.remove(e));
    v.camera.moveEnd.removeEventListener(handleCameraMoveForShotFrame);
    isGeometryEditMode.value = false;
    modifiedSubIds.value.clear();
    editPoints.value = [];
    refreshMvt();
  };

  const confirmGeometryEdit = async (refreshMvt: () => void) => {
    if (!selectedFeature.value || !selectedFeatureId.value) return;

    // Logic for saving without draggers (based on new positions if needed, or re-fetch)
    exitEditMode(refreshMvt);
    if (projectId.value) store.dispatch('geodata/fetchVectorSummaryForProject', projectId.value);
  };

  return {
    isZoomHighEnough,
    isLoadingParts,
    enterGeometryEditMode,
    confirmGeometryEdit,
    exitEditMode
  };
}
