import { ref, watch, shallowRef, toRaw, computed, type Ref } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
import type { ProjectRaster, TerrainLayer } from '@/types/api';
import { buildTiTilerStyleParams, getExtentFromGeometry } from '@/util/titiler-style-builder';

export function useCesiumImagery(
  viewer: Ref<Cesium.Viewer | null>,
  terrainLayers: Ref<TerrainLayer[]>,
  raiseMvt: () => void
) {
  const store = useStore();
  const visibleLayerIds = computed<string[]>({
    get: () => store.state.geodata.visibleRasterIds || [],
    set: (val) => store.commit('geodata/SET_VISIBLE_RASTER_IDS', val)
  });
  const layerOpacities = computed<Record<string, number>>(() => store.state.geodata.rasterOpacities || {});

  const visibleGlobalRasterIds = computed<string[]>({
    get: () => store.state.geodata.visibleGlobalRasterIds || [],
    set: (val) => store.commit('geodata/SET_VISIBLE_GLOBAL_RASTER_IDS', val)
  });
  const globalRasterOpacities = computed<Record<string, number>>(() => store.state.geodata.globalRasterOpacities || {});

  const activeImageryLayers = shallowRef<Record<string, Cesium.ImageryLayer>>({});
  const selectedTerrainId = ref<string | null>(null);

  const setLayerOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageryLayers.value[layerId];
    if (layer) {
      layer.alpha = opacity / 100;
      store.commit('geodata/SET_RASTER_OPACITY', { rasterId: layerId, opacity });
    }
  };

  const setGlobalRasterOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageryLayers.value[layerId];
    if (layer) {
      layer.alpha = opacity / 100;
      store.commit('geodata/SET_GLOBAL_RASTER_OPACITY', { rasterId: layerId, opacity });
    }
  };

  const toggleImageryLayer = (layerInfo: any, forceReload = false) => {
    const v = viewer.value;
    if (!v) return;
    const isVisible = visibleLayerIds.value.includes(layerInfo.id) || visibleGlobalRasterIds.value.includes(layerInfo.id);

    if (isVisible) {
      if (activeImageryLayers.value[layerInfo.id]) {
        if (!forceReload) return;
        v.imageryLayers.remove(toRaw(activeImageryLayers.value[layerInfo.id]));
        const nextLayers = { ...activeImageryLayers.value };
        delete nextLayers[layerInfo.id];
        activeImageryLayers.value = nextLayers;
      }

      const colormapParam = buildTiTilerStyleParams(layerInfo.style, layerInfo.colormapId, layerInfo.resampling, layerInfo.characteristics);

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
      const currentOpacity = layerOpacities.value[layerInfo.id] ?? globalRasterOpacities.value[layerInfo.id];
      if (currentOpacity === undefined) {
        if (visibleGlobalRasterIds.value.includes(layerInfo.id)) {
          store.commit('geodata/SET_GLOBAL_RASTER_OPACITY', { rasterId: layerInfo.id, opacity: 100 });
        } else {
          store.commit('geodata/SET_RASTER_OPACITY', { rasterId: layerInfo.id, opacity: 100 });
        }
      }
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
    store.commit('geodata/SET_VISIBLE_RASTER_IDS', []);
    store.commit('geodata/SET_VISIBLE_GLOBAL_RASTER_IDS', []);
  };

  watch(visibleLayerIds, (newIds) => {
    // Remove layers no longer in visible list
    for (const id in activeImageryLayers.value) {
      if (!newIds.includes(id) && !visibleGlobalRasterIds.value.includes(id)) {
        const layer = activeImageryLayers.value[id];
        if (layer) {
          viewer.value?.imageryLayers.remove(toRaw(layer));
          const nextLayers = { ...activeImageryLayers.value };
          delete nextLayers[id];
          activeImageryLayers.value = nextLayers;
        }
      }
    }
    // Add layers in visible list but not currently rendered
    const layers = store.state.geodata.projectRasters?.content || [];
    for (const id of newIds) {
      if (!activeImageryLayers.value[id]) {
        const layerInfo = layers.find((l: any) => l.id === id);
        if (layerInfo) {
          toggleImageryLayer(layerInfo);
        }
      }
    }
  }, { deep: true });

  watch(visibleGlobalRasterIds, (newIds) => {
    // Remove layers no longer in visible list
    for (const id in activeImageryLayers.value) {
      if (!visibleLayerIds.value.includes(id) && !newIds.includes(id)) {
        const layer = activeImageryLayers.value[id];
        if (layer) {
          viewer.value?.imageryLayers.remove(toRaw(layer));
          const nextLayers = { ...activeImageryLayers.value };
          delete nextLayers[id];
          activeImageryLayers.value = nextLayers;
        }
      }
    }
    // Add layers in visible list but not currently rendered
    const layers = store.state.geodata.globalRasters || [];
    for (const id of newIds) {
      if (!activeImageryLayers.value[id]) {
        const layerInfo = layers.find((l: any) => l.id === id);
        if (layerInfo) {
          toggleImageryLayer(layerInfo);
        }
      }
    }
  }, { deep: true });

  watch(
    () => {
      const visibleRasters = store.state.geodata.projectRasters?.content?.filter((r: any) =>
        visibleLayerIds.value.includes(r.id)
      ) || [];
      return visibleRasters.map((r: any) => ({
        id: r.id,
        colormapId: r.colormapId,
        resampling: r.resampling,
        styleId: r.style?.id,
        rescaleMin: r.characteristics?.rescaleMin,
        rescaleMax: r.characteristics?.rescaleMax,
      }));
    },
    (newVal, oldVal) => {
      newVal.forEach((newLayer: any) => {
        const oldLayer = oldVal?.find((o: any) => o.id === newLayer.id);
        if (!oldLayer ||
            oldLayer.colormapId !== newLayer.colormapId ||
            oldLayer.resampling !== newLayer.resampling ||
            oldLayer.styleId !== newLayer.styleId ||
            oldLayer.rescaleMin !== newLayer.rescaleMin ||
            oldLayer.rescaleMax !== newLayer.rescaleMax
        ) {
          const layerInfo = store.state.geodata.projectRasters?.content?.find((l: any) => l.id === newLayer.id);
          if (layerInfo) {
            toggleImageryLayer(layerInfo, true);
          }
        }
      });
    },
    { deep: true }
  );

  watch(
    () => {
      const visibleRasters = store.state.geodata.globalRasters?.filter((r: any) =>
        visibleGlobalRasterIds.value.includes(r.id)
      ) || [];
      return visibleRasters.map((r: any) => ({
        id: r.id,
        colormapId: r.colormapId,
        resampling: r.resampling,
        styleId: r.style?.id,
        rescaleMin: r.characteristics?.rescaleMin,
        rescaleMax: r.characteristics?.rescaleMax,
      }));
    },
    (newVal, oldVal) => {
      newVal.forEach((newLayer: any) => {
        const oldLayer = oldVal?.find((o: any) => o.id === newLayer.id);
        if (!oldLayer ||
            oldLayer.colormapId !== newLayer.colormapId ||
            oldLayer.resampling !== newLayer.resampling ||
            oldLayer.styleId !== newLayer.styleId ||
            oldLayer.rescaleMin !== newLayer.rescaleMin ||
            oldLayer.rescaleMax !== newLayer.rescaleMax
        ) {
          const layerInfo = store.state.geodata.globalRasters?.find((l: any) => l.id === newLayer.id);
          if (layerInfo) {
            toggleImageryLayer(layerInfo, true);
          }
        }
      });
    },
    { deep: true }
  );

  watch(layerOpacities, (newOpacities) => {
    for (const id in activeImageryLayers.value) {
      const layer = activeImageryLayers.value[id];
      if (layer && newOpacities[id] !== undefined) {
        layer.alpha = newOpacities[id] / 100;
      }
    }
  }, { deep: true });

  watch(globalRasterOpacities, (newOpacities) => {
    for (const id in activeImageryLayers.value) {
      const layer = activeImageryLayers.value[id];
      if (layer && newOpacities[id] !== undefined) {
        layer.alpha = newOpacities[id] / 100;
      }
    }
  }, { deep: true });

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
    visibleGlobalRasterIds,
    activeImageryLayers,
    layerOpacities,
    globalRasterOpacities,
    selectedTerrainId,
    setLayerOpacity,
    setGlobalRasterOpacity,
    toggleImageryLayer,
    clearImageryLayers
  };
}
