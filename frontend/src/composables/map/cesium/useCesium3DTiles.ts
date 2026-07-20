import { ref, watch, shallowRef, computed, type Ref } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
import type { ThreeDTilesLayer } from '@/types/api';

export function useCesium3DTiles(
  viewer: Ref<Cesium.Viewer | null>
) {
  const store = useStore();

  const threeDTilesLayers = computed<ThreeDTilesLayer[]>(
    () => store.state.geodata.threeDTilesLayers?.content || []
  );

  const visible3DTilesIds = computed<string[]>({
    get: () => store.state.geodata.visible3DTilesIds || [],
    set: (val) => store.commit('geodata/SET_VISIBLE_3D_TILES_IDS', val)
  });

  const active3DTilesets = shallowRef<Record<string, Cesium.Cesium3DTileset>>({});

  const toggle3DTileset = async (layerInfo: ThreeDTilesLayer) => {
    const v = viewer.value;
    if (!v) return;

    const isVisible = visible3DTilesIds.value.includes(layerInfo.id);

    if (isVisible) {
      if (!active3DTilesets.value[layerInfo.id]) {
        try {
          console.log(`Loading 3D Tileset from URL: ${layerInfo.tilesetUrl}`);
          const tileset = await Cesium.Cesium3DTileset.fromUrl(layerInfo.tilesetUrl);
          v.scene.primitives.add(tileset);
          tileset.show = true;

          active3DTilesets.value = {
            ...active3DTilesets.value,
            [layerInfo.id]: tileset
          };
        } catch (error) {
          console.error(`Failed to load 3D Tileset for layer ${layerInfo.id}:`, error);
        }
      } else {
        const tileset = active3DTilesets.value[layerInfo.id];
        if (tileset) {
          tileset.show = true;
        }
      }
    } else {
      const tileset = active3DTilesets.value[layerInfo.id];
      if (tileset) {
        tileset.show = false;
      }
    }
  };

  const zoomTo3DTileset = (layerId: string) => {
    const v = viewer.value;
    const tileset = active3DTilesets.value[layerId];
    if (v && tileset) {
      v.zoomTo(tileset);
    }
  };

  const clear3DTilesets = () => {
    const v = viewer.value;
    if (!v) return;
    for (const id in active3DTilesets.value) {
      const tileset = active3DTilesets.value[id];
      if (tileset) {
        v.scene.primitives.remove(tileset);
      }
    }
    active3DTilesets.value = {};
    store.commit('geodata/SET_VISIBLE_3D_TILES_IDS', []);
  };

  watch(visible3DTilesIds, (newIds) => {
    // Hide or show tilesets according to newIds
    for (const layer of threeDTilesLayers.value) {
      toggle3DTileset(layer);
    }
  }, { deep: true });

  return {
    threeDTilesLayers,
    visible3DTilesIds,
    active3DTilesets,
    toggle3DTileset,
    zoomTo3DTileset,
    clear3DTilesets
  };
}
