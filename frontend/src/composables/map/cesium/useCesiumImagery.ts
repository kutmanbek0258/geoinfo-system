import { ref, watch, shallowRef, toRaw, type Ref } from 'vue';
import * as Cesium from 'cesium';
import type { ImageryLayer, TerrainLayer } from '@/types/api';
import { buildTiTilerStyleParams, getExtentFromGeometry } from '@/util/titiler-style-builder';

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

  const toggleImageryLayer = (layerInfo: ImageryLayer, forceReload = false) => {
    const v = viewer.value;
    if (!v) return;
    const isVisible = visibleLayerIds.value.includes(layerInfo.id);

    if (isVisible) {
      if (activeImageryLayers.value[layerInfo.id]) {
        if (!forceReload) return;
        v.imageryLayers.remove(toRaw(activeImageryLayers.value[layerInfo.id]));
        const nextLayers = { ...activeImageryLayers.value };
        delete nextLayers[layerInfo.id];
        activeImageryLayers.value = nextLayers;
      }

      const colormapParam = buildTiTilerStyleParams(layerInfo.style, layerInfo.colormapId, layerInfo.resampling);

      const s3Url = `s3://geo-abstraction-input/${layerInfo.cogObjectKey}`;
      const tileUrl = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(s3Url)}${colormapParam}`;

      let cesiumRectangle: Cesium.Rectangle | undefined;
      if (layerInfo.bbox) {
        const extent = getExtentFromGeometry(layerInfo.bbox);
        if (extent) {
          cesiumRectangle = Cesium.Rectangle.fromDegrees(extent[0], extent[1], extent[2], extent[3]);
        }
      }

      const provider = new Cesium.UrlTemplateImageryProvider({
        url: tileUrl,
        rectangle: cesiumRectangle
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
