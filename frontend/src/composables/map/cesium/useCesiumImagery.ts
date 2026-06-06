import { ref, watch, shallowRef, toRaw, type Ref } from 'vue';
import * as Cesium from 'cesium';
import type { ImageryLayer, TerrainLayer } from '@/types/api';

export function useCesiumImagery(
  viewer: Ref<Cesium.Viewer | null>,
  terrainLayers: Ref<TerrainLayer[]>,
  raiseMvt: () => void
) {
  const visibleLayerIds = ref<string[]>([]);
  const activeImageryLayers = shallowRef<Record<string, Cesium.ImageryLayer>>({});
  const layerOpacities = ref<Record<string, number>>({});
  const selectedTerrainId = ref<string | null>(null);

  const setLayerOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageryLayers.value[layerId];
    if (layer) {
      layer.alpha = opacity / 100;
      layerOpacities.value[layerId] = opacity;
    }
  };

  const toggleImageryLayer = (layerInfo: ImageryLayer) => {
    const v = viewer.value;
    if (!v) return;
    const isVisible = visibleLayerIds.value.includes(layerInfo.id);

    if (isVisible) {
      if (activeImageryLayers.value[layerInfo.id]) return;

      const provider = new Cesium.WebMapServiceImageryProvider({
        url: layerInfo.serviceUrl,
        layers: layerInfo.workspace + ":" + layerInfo.layerName,
        parameters: { transparent: 'true', format: 'image/png' },
      });
      const layer = v.imageryLayers.addImageryProvider(provider);
      layer.alpha = (layerOpacities.value[layerInfo.id] || 100) / 100;
      
      activeImageryLayers.value = { ...activeImageryLayers.value, [layerInfo.id]: layer };
      if (layerOpacities.value[layerInfo.id] === undefined) layerOpacities.value[layerInfo.id] = 100;
      raiseMvt();
    } else {
      const layer = activeImageryLayers.value[layerInfo.id];
      if (layer) {
        v.imageryLayers.remove(toRaw(layer));
        const nextLayers = { ...activeImageryLayers.value };
        delete nextLayers[layerInfo.id];
        activeImageryLayers.value = nextLayers;
      }
    }
  };

  const clearImageryLayers = () => {
    const v = viewer.value;
    if (!v) return;
    for (const id in activeImageryLayers.value) {
      v.imageryLayers.remove(toRaw(activeImageryLayers.value[id]));
    }
    activeImageryLayers.value = {};
    visibleLayerIds.value = [];
    layerOpacities.value = {};
  };

  watch(selectedTerrainId, async (newId) => {
    const v = viewer.value;
    if (!v) return;
    if (!newId) {
      v.terrainProvider = new Cesium.EllipsoidTerrainProvider();
    } else {
      const layer = terrainLayers.value.find(l => l.id === newId);
      if (layer) {
        v.terrainProvider = await Cesium.CesiumTerrainProvider.fromUrl(layer.terrainUrl);
      }
    }
  });

  return {
    visibleLayerIds,
    activeImageryLayers,
    layerOpacities,
    selectedTerrainId,
    setLayerOpacity,
    toggleImageryLayer,
    clearImageryLayers
  };
}
