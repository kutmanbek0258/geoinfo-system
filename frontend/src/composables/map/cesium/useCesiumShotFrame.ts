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
  const draggerEntities = ref<Cesium.Entity[]>([]);
  let activeDragger: Cesium.Entity | null = null;
  let editHandler: Cesium.ScreenSpaceEventHandler | null = null;

  const addDraggersForEntity = (v: Cesium.Viewer, entity: Cesium.Entity, subId: number) => {
    let positions: Cesium.Cartesian3[] = [];
    if (entity.position) {
      const pos = entity.position.getValue(Cesium.JulianDate.now());
      if (pos) positions = [pos];
    } else if (entity.polyline?.positions) {
      const pos = entity.polyline.positions.getValue(Cesium.JulianDate.now()) as Cesium.Cartesian3[];
      if (pos) positions = [...pos];
    } else if (entity.polygon?.hierarchy) {
      const hierarchy = entity.polygon.hierarchy.getValue(Cesium.JulianDate.now()) as Cesium.PolygonHierarchy;
      if (hierarchy) positions = [...hierarchy.positions];
    }

    positions.forEach((pos, index) => {
      const dragger = v.entities.add({
        position: new Cesium.CallbackProperty(() => {
          const group = editPoints.value.find(g => g.subId === subId);
          return group?.points[index] || pos;
        }, false) as any,
        point: { 
          pixelSize: 12, 
          color: Cesium.Color.RED, 
          outlineColor: Cesium.Color.WHITE, 
          outlineWidth: 2, 
          disableDepthTestDistance: Number.POSITIVE_INFINITY 
        },
      });
      (dragger as any).userData = { subId, index };
      draggerEntities.value.push(dragger);
    });
    
    let group = editPoints.value.find(g => g.subId === subId);
    if (!group) {
      group = { subId, points: [...positions] };
      editPoints.value.push(group);
    }
  };

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

        const createdEntities = createEntitiesFromGeoJSON(v, JSON.parse(part.geojson), {
          id: partId, name: `${selectedFeature.value?.name} (Part ${part.subId})`,
          sub_id: part.subId, show: true, style: selectedFeature.value?.characteristics?.style
        });
        
        createdEntities.forEach(entity => {
          if (!entity.id.endsWith('-outline')) addDraggersForEntity(v, entity, part.subId);
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
    draggerEntities.value = [];
    modifiedSubIds.value.clear();
    refreshMvt();

    editHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
    editHandler.setInputAction((click: any) => {
      const picked = v.scene.pick(click.position);
      if (Cesium.defined(picked) && picked.id && draggerEntities.value.includes(picked.id)) {
        activeDragger = picked.id;
        v.scene.screenSpaceCameraController.enableRotate = false;
      }
    }, Cesium.ScreenSpaceEventType.LEFT_DOWN);

    editHandler.setInputAction((movement: any) => {
      if (activeDragger) {
        const position = v.scene.pickPosition(movement.endPosition);
        if (Cesium.defined(position)) {
          const { subId, index } = (activeDragger as any).userData;
          const group = editPoints.value.find(g => g.subId === subId);
          if (group) {
            group.points[index] = position;
            modifiedSubIds.value.add(subId);
          }
        }
      }
    }, Cesium.ScreenSpaceEventType.MOUSE_MOVE);

    editHandler.setInputAction(() => {
      activeDragger = null;
      v.scene.screenSpaceCameraController.enableRotate = true;
    }, Cesium.ScreenSpaceEventType.LEFT_UP);

    v.camera.moveEnd.addEventListener(handleCameraMoveForShotFrame);
    fetchParts();
  };

  const exitEditMode = (refreshMvt: () => void) => {
    const v = viewer.value;
    if (!v) return;
    if (editHandler) { editHandler.destroy(); editHandler = null; }
    draggerEntities.value.forEach(e => v.entities.remove(e));
    draggerEntities.value = [];
    v.entities.values.filter(e => e.id.startsWith('edit_')).forEach(e => v.entities.remove(e));
    v.camera.moveEnd.removeEventListener(handleCameraMoveForShotFrame);
    isGeometryEditMode.value = false;
    modifiedSubIds.value.clear();
    editPoints.value = [];
    refreshMvt();
  };

  const confirmGeometryEdit = async (refreshMvt: () => void) => {
    if (!selectedFeature.value || !selectedFeatureId.value) return;

    if (modifiedSubIds.value.size > 0) {
      const partsToUpdate = [];
      for (const group of editPoints.value) {
        if (modifiedSubIds.value.has(group.subId)) {
          const sampled = await sampleHeights(group.points);
          let geojson;
          if (selectedFeature.value.type === 'Point') geojson = JSON.stringify({ type: 'Point', coordinates: sampled[0] });
          else if (selectedFeature.value.type === 'MultiLineString') geojson = JSON.stringify({ type: 'LineString', coordinates: sampled });
          else geojson = JSON.stringify({ type: 'Polygon', coordinates: [[...sampled, sampled[0]]] });
          partsToUpdate.push({ subId: group.subId, geojson });
        }
      }
      const typeMap: any = { 'Point': 'points', 'MultiLineString': 'multilines', 'Polygon': 'polygons' };
      if (partsToUpdate.length > 0) await GeodataService.updateGeometryParts(typeMap[selectedFeature.value.type], selectedFeatureId.value, partsToUpdate);
    }

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
