import { ref, shallowRef, toRaw, type Ref } from 'vue';
import { Map } from 'ol';
import TileLayer from 'ol/layer/Tile';
import TileWMS from 'ol/source/TileWMS.js';
import type { ImageryLayer } from '@/types/api';

export function useOlWms(map: Ref<Map | null>) {
  const activeImageLayers = shallowRef<Record<string, TileLayer<TileWMS>>>({});
  const layerOpacities = ref<Record<string, number>>({});
  const visibleLayerIds = ref<string[]>([]);

  const setLayerOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageLayers.value[layerId];
    if (layer) {
      toRaw(layer).setOpacity(opacity / 100);
      layerOpacities.value[layerId] = opacity;
    }
  };

  const toggleImageryLayer = (layerInfo: ImageryLayer) => {
    const m = map.value;
    if (!m) return;
    const isVisible = visibleLayerIds.value.includes(layerInfo.id);

    if (isVisible) {
      if (activeImageLayers.value[layerInfo.id]) return;

      const wmsSource = new TileWMS({
        url: layerInfo.serviceUrl,
        params: {
          'LAYERS': layerInfo.workspace + ":" + layerInfo.layerName,
          TILED: true,
        },
        serverType: 'geoserver',
        transition: 0,
      });
      const imageLayer = new TileLayer({
        source: wmsSource,
        opacity: (layerOpacities.value[layerInfo.id] || 100) / 100,
      });
      m.addLayer(imageLayer);

      activeImageLayers.value = {
        ...activeImageLayers.value,
        [layerInfo.id]: imageLayer
      };

      if (layerOpacities.value[layerInfo.id] === undefined) {
        layerOpacities.value[layerInfo.id] = 100;
      }
    } else {
      const layerToRemove = activeImageLayers.value[layerInfo.id];
      if (layerToRemove) {
        m.removeLayer(toRaw(layerToRemove));

        const nextLayers = { ...activeImageLayers.value };
        delete nextLayers[layerInfo.id];
        activeImageLayers.value = nextLayers;
      }
    }
  };

  const clearWmsLayers = () => {
    const m = map.value;
    if (!m) return;
    for (const key in activeImageLayers.value) {
      const layerProxy = activeImageLayers.value[key];
      if (layerProxy) {
        m.removeLayer(toRaw(layerProxy));
      }
    }
    activeImageLayers.value = {};
    visibleLayerIds.value = [];
    layerOpacities.value = {};
  };

  return {
    activeImageLayers,
    layerOpacities,
    visibleLayerIds,
    setLayerOpacity,
    toggleImageryLayer,
    clearWmsLayers
  };
}
