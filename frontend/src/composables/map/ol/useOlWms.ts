import { ref, shallowRef, toRaw, computed, watch, type Ref } from 'vue';
import { useStore } from 'vuex';
import { Map } from 'ol';
import TileLayer from 'ol/layer/Tile';
import XYZ from 'ol/source/XYZ';
import type { ProjectRaster } from '@/types/api';
import { buildTiTilerStyleParams, getExtentFromGeometry } from '@/util/titiler-style-builder';
import { transformExtent } from 'ol/proj';

export function useOlWms(map: Ref<Map | null>) {
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

  const activeImageLayers = shallowRef<Record<string, TileLayer<XYZ>>>({});

  const setLayerOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageLayers.value[layerId];
    if (layer) {
      toRaw(layer).setOpacity(opacity / 100);
      store.commit('geodata/SET_RASTER_OPACITY', { rasterId: layerId, opacity });
    }
  };

  const setGlobalRasterOpacity = (layerId: string, opacity: number) => {
    const layer = activeImageLayers.value[layerId];
    if (layer) {
      toRaw(layer).setOpacity(opacity / 100);
      store.commit('geodata/SET_GLOBAL_RASTER_OPACITY', { rasterId: layerId, opacity });
    }
  };

  const toggleImageryLayer = (layerInfo: any, forceReload = false) => {
    const m = map.value;
    if (!m) return;
    const isVisible = visibleLayerIds.value.includes(layerInfo.id) || visibleGlobalRasterIds.value.includes(layerInfo.id);

    if (isVisible) {
      if (activeImageLayers.value[layerInfo.id]) {
        if (!forceReload) return;
        m.removeLayer(toRaw(activeImageLayers.value[layerInfo.id]));
        const nextLayers = { ...activeImageLayers.value };
        delete nextLayers[layerInfo.id];
        activeImageLayers.value = nextLayers;
      }

      const colormapParam = buildTiTilerStyleParams(layerInfo.style, layerInfo.colormapId, layerInfo.resampling, layerInfo.characteristics);

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
      const currentOpacity = layerOpacities.value[layerInfo.id] ?? globalRasterOpacities.value[layerInfo.id] ?? 100;
      const imageLayer = new TileLayer({
        source: xyzSource,
        opacity: currentOpacity / 100,
        extent: olExtent
      });
      m.addLayer(imageLayer);

      activeImageLayers.value = {
        ...activeImageLayers.value,
        [layerInfo.id]: imageLayer
      };

      if (layerOpacities.value[layerInfo.id] === undefined && globalRasterOpacities.value[layerInfo.id] === undefined) {
        if (visibleGlobalRasterIds.value.includes(layerInfo.id)) {
          store.commit('geodata/SET_GLOBAL_RASTER_OPACITY', { rasterId: layerInfo.id, opacity: 100 });
        } else {
          store.commit('geodata/SET_RASTER_OPACITY', { rasterId: layerInfo.id, opacity: 100 });
        }
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
    store.commit('geodata/SET_VISIBLE_RASTER_IDS', []);
    store.commit('geodata/SET_VISIBLE_GLOBAL_RASTER_IDS', []);
  };

  watch(visibleLayerIds, (newIds) => {
    // Remove layers no longer in visible list
    for (const id in activeImageLayers.value) {
      if (!newIds.includes(id) && !visibleGlobalRasterIds.value.includes(id)) {
        const layer = activeImageLayers.value[id];
        if (layer) {
          map.value?.removeLayer(toRaw(layer));
          const nextLayers = { ...activeImageLayers.value };
          delete nextLayers[id];
          activeImageLayers.value = nextLayers;
        }
      }
    }
    // Add layers in visible list but not currently rendered
    const layers = store.state.geodata.projectRasters?.content || [];
    for (const id of newIds) {
      if (!activeImageLayers.value[id]) {
        const layerInfo = layers.find((l: any) => l.id === id);
        if (layerInfo) {
          toggleImageryLayer(layerInfo);
        }
      }
    }
  }, { deep: true });

  watch(visibleGlobalRasterIds, (newIds) => {
    // Remove layers no longer in visible list
    for (const id in activeImageLayers.value) {
      if (!visibleLayerIds.value.includes(id) && !newIds.includes(id)) {
        const layer = activeImageLayers.value[id];
        if (layer) {
          map.value?.removeLayer(toRaw(layer));
          const nextLayers = { ...activeImageLayers.value };
          delete nextLayers[id];
          activeImageLayers.value = nextLayers;
        }
      }
    }
    // Add layers in visible list but not currently rendered
    const layers = store.state.geodata.globalRasters || [];
    for (const id of newIds) {
      if (!activeImageLayers.value[id]) {
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
    for (const id in activeImageLayers.value) {
      const layer = activeImageLayers.value[id];
      if (layer && newOpacities[id] !== undefined) {
        toRaw(layer).setOpacity(newOpacities[id] / 100);
      }
    }
  }, { deep: true });

  watch(globalRasterOpacities, (newOpacities) => {
    for (const id in activeImageLayers.value) {
      const layer = activeImageLayers.value[id];
      if (layer && newOpacities[id] !== undefined) {
        toRaw(layer).setOpacity(newOpacities[id] / 100);
      }
    }
  }, { deep: true });

  return {
    activeImageLayers,
    layerOpacities,
    globalRasterOpacities,
    visibleLayerIds,
    visibleGlobalRasterIds,
    setLayerOpacity,
    setGlobalRasterOpacity,
    toggleImageryLayer,
    clearWmsLayers
  };
}
