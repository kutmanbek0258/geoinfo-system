import { ref, shallowRef, toRaw, type Ref } from 'vue';
import { Map } from 'ol';
import TileLayer from 'ol/layer/Tile';
import XYZ from 'ol/source/XYZ';
import type { ImageryLayer } from '@/types/api';
import { buildTiTilerStyleParams, getExtentFromGeometry } from '@/util/titiler-style-builder';
import { transformExtent } from 'ol/proj';

export function useOlWms(map: Ref<Map | null>) {
  const activeImageLayers = shallowRef<Record<string, TileLayer<XYZ>>>({});
  const layerOpacities = ref<Record<string, number>>({});
  const visibleLayerIds = ref<string[]>([]);

  const setLayerOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageLayers.value[layerId];
    if (layer) {
      toRaw(layer).setOpacity(opacity / 100);
      layerOpacities.value[layerId] = opacity;
    }
  };

  const toggleImageryLayer = (layerInfo: ImageryLayer, forceReload = false) => {
    const m = map.value;
    if (!m) return;
    const isVisible = visibleLayerIds.value.includes(layerInfo.id);

    if (isVisible) {
      if (activeImageLayers.value[layerInfo.id]) {
        if (!forceReload) return;
        m.removeLayer(toRaw(activeImageLayers.value[layerInfo.id]));
        const nextLayers = { ...activeImageLayers.value };
        delete nextLayers[layerInfo.id];
        activeImageLayers.value = nextLayers;
      }

      const colormapParam = buildTiTilerStyleParams(layerInfo.style, layerInfo.colormapId, layerInfo.resampling);

      const s3Url = `s3://geo-abstraction-input/${layerInfo.cogObjectKey}`;
      const tileUrl = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(s3Url)}${colormapParam}`;

      let olExtent: [number, number, number, number] | undefined;
      if (layerInfo.bbox) {
        const wgs84Extent = getExtentFromGeometry(layerInfo.bbox);
        if (wgs84Extent) {
          olExtent = transformExtent(wgs84Extent, 'EPSG:4326', 'EPSG:3857') as [number, number, number, number];
        }
      }

      const xyzSource = new XYZ({
        url: tileUrl,
        transition: 0,
      });
      const imageLayer = new TileLayer({
        source: xyzSource,
        opacity: (layerOpacities.value[layerInfo.id] || 100) / 100,
        extent: olExtent
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
