import { ref, type Ref } from 'vue';
import { useStore } from 'vuex';
import { debounce } from 'lodash';
import { Map } from 'ol';
import { Modify } from 'ol/interaction';
import { GeoJSON } from 'ol/format';
import { toLonLat } from 'ol/proj';
import GeodataService from '@/services/geodata.service';
import type VectorSource from 'ol/source/Vector';

export function useOlShotFrame(
  map: Ref<Map | null>,
  projectId: Ref<string>,
  selectedFeatureId: Ref<string | null>,
  selectedFeature: Ref<any | null>,
  isGeometryEditMode: Ref<boolean>,
  tempSource: VectorSource
) {
  const store = useStore();
  const geoJsonFormat = new GeoJSON();
  const SHOT_FRAME_MIN_ZOOM = Number(import.meta.env.VITE_SHOT_FRAME_MIN_ZOOM || 16);
  
  const isZoomHighEnough = ref(false);
  const modifiedSubIds = ref(new Set<number>());
  const isLoadingParts = ref(false);
  
  let modifyInteraction: Modify | null = null;

  const fetchParts = debounce(async () => {
    const m = map.value;
    if (!isGeometryEditMode.value || !isZoomHighEnough.value || !selectedFeatureId.value || !selectedFeature.value || !m) return;

    const view = m.getView();
    const extent = view.calculateExtent(m.getSize());
    const minXminY = toLonLat([extent[0], extent[1]]);
    const maxXmaxY = toLonLat([extent[2], extent[3]]);

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
        apiType,
        selectedFeatureId.value,
        minXminY[0], minXminY[1], maxXmaxY[0], maxXmaxY[1]
      );
      
      const parts = response.data;
      const features = parts.map((part: any) => {
        const result = geoJsonFormat.readFeature(part.geojson, {
          dataProjection: 'EPSG:4326',
          featureProjection: 'EPSG:3857'
        });
        const feature = Array.isArray(result) ? result[0] : result;
        if (feature) {
          (feature as any).set('sub_id', part.subId);
          (feature as any).setId(`${selectedFeatureId.value}_${part.subId}`);
        }
        return feature;
      }).filter((f: any) => !!f);

      features.forEach((f: any) => {
        if (!tempSource.getFeatureById(f.getId() as string)) {
          tempSource.addFeature(f);
        }
      });
    } catch (err) {
      console.error("Failed to fetch geometry parts:", err);
    } finally {
      isLoadingParts.value = false;
    }
  }, 1000);

  const handleMapMoveForShotFrame = () => {
    const m = map.value;
    if (!isGeometryEditMode.value || !m) return;
    
    const currentZoom = m.getView().getZoom() || 0;
    isZoomHighEnough.value = currentZoom >= SHOT_FRAME_MIN_ZOOM;
    
    if (isZoomHighEnough.value) {
      fetchParts();
    }
  };

  const enterGeometryEditMode = () => {
    const m = map.value;
    if (!m || !selectedFeatureId.value || !selectedFeature.value) return;

    const currentZoom = m.getView().getZoom() || 0;
    isZoomHighEnough.value = currentZoom >= SHOT_FRAME_MIN_ZOOM;

    if (!isZoomHighEnough.value) {
      alert(`Please zoom in to at least level ${SHOT_FRAME_MIN_ZOOM} to edit this complex object.`);
      return;
    }

    // Lock zoom to min level 16
    m.getView().setMinZoom(SHOT_FRAME_MIN_ZOOM);

    isGeometryEditMode.value = true;
    modifiedSubIds.value.clear();
    tempSource.clear();

    modifyInteraction = new Modify({
      source: tempSource,
    });
    
    modifyInteraction.on('modifyend', (evt) => {
      evt.features.forEach((f: any) => {
        const subId = f.get('sub_id');
        if (subId !== undefined) {
          modifiedSubIds.value.add(subId);
        }
      });
    });

    m.addInteraction(modifyInteraction);
    m.on('moveend', handleMapMoveForShotFrame);
    fetchParts();
  };

  const exitGeometryEditMode = () => {
    const m = map.value;
    if (m) {
      // Reset zoom constraint
      m.getView().setMinZoom(0);

      if (modifyInteraction) {
        m.removeInteraction(modifyInteraction);
        modifyInteraction = null;
      }
      m.un('moveend', handleMapMoveForShotFrame);
    }
    isGeometryEditMode.value = false;
    modifiedSubIds.value.clear();
    tempSource.clear();
  };

  const confirmGeometryEdit = async (refreshMvt: () => void) => {
    if (!selectedFeature.value || !selectedFeatureId.value) return;

    const apiTypeMap: Record<string, 'points' | 'multilines' | 'polygons'> = {
      'Point': 'points',
      'MultiLineString': 'multilines',
      'Polygon': 'polygons'
    };
    const apiType = apiTypeMap[selectedFeature.value.type];

    if (modifiedSubIds.value.size > 0) {
      const partsToUpdate = Array.from(modifiedSubIds.value).map(subId => {
        const feature = tempSource.getFeatureById(`${selectedFeatureId.value}_${subId}`);
        if (!feature) return null;
        
        const geom = feature.getGeometry();
        if (!geom) return null;
        
        const geojson = geoJsonFormat.writeGeometry(geom, {
          featureProjection: 'EPSG:3857',
          dataProjection: 'EPSG:4326'
        });
        
        return { subId, geojson };
      }).filter(p => p !== null) as { subId: number, geojson: string }[];

      if (partsToUpdate.length > 0) {
        await GeodataService.updateGeometryParts(apiType, selectedFeatureId.value, partsToUpdate);
      }
    }

    exitGeometryEditMode();
    refreshMvt();
    
    if (projectId.value) {
      store.dispatch('geodata/fetchVectorDataForProject', projectId.value);
    }
  };

  const cancelGeometryEdit = () => {
    exitGeometryEditMode();
    if (projectId.value) {
      store.dispatch('geodata/fetchVectorDataForProject', projectId.value);
    }
  };

  return {
    isZoomHighEnough,
    isLoadingParts,
    enterGeometryEditMode,
    confirmGeometryEdit,
    cancelGeometryEdit,
    exitGeometryEditMode,
    SHOT_FRAME_MIN_ZOOM
  };
}
