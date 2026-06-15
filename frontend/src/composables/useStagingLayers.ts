import { watch, onBeforeUnmount } from 'vue';
import { useStore } from 'vuex';
import OlMap from 'ol/Map';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import MVT from 'ol/format/MVT';
import { Fill, Stroke, Style, Circle as CircleStyle } from 'ol/style';

/** Bright highlight style for analysis result geometries */
const stagingStyle = new Style({
    stroke: new Stroke({ color: '#ff6b35', width: 2.5 }),
    fill:   new Fill({ color: 'rgba(255, 107, 53, 0.15)' }),
    image:  new CircleStyle({
        radius: 6,
        fill:   new Fill({ color: '#ff6b35' }),
        stroke: new Stroke({ color: '#fff', width: 1.5 }),
    }),
});

/**
 * Composable that watches `geodata.stagingLayers` in the Vuex store and
 * synchronises OpenLayers VectorTileLayer instances on the provided map.
 * Layers are keyed by taskId so they can be added/removed independently.
 */
export function useStagingLayers(map: OlMap) {
    const store = useStore();
    // Use a plain object to avoid conflict with the native JS Map / OL Map class names
    const layerRegistry: Record<string, VectorTileLayer> = {};

    function buildOlLayer(url: string, label: string): VectorTileLayer {
        return new VectorTileLayer({
            source: new VectorTileSource({
                format: new MVT(),
                url,
            }),
            style: stagingStyle,
            properties: { isStagingLayer: true, label },
            zIndex: 100,
        });
    }

    function syncLayers(stagingLayers: Array<{ taskId: string; type: string; url: string; label: string }>) {
        const incomingIds = new Set(stagingLayers.map(l => l.taskId));

        // Remove layers that are no longer in state
        for (const taskId of Object.keys(layerRegistry)) {
            if (!incomingIds.has(taskId)) {
                map.removeLayer(layerRegistry[taskId]);
                delete layerRegistry[taskId];
            }
        }

        // Add new layers
        for (const sl of stagingLayers) {
            if (sl.type === 'VECTOR' && !layerRegistry[sl.taskId]) {
                const olLayer = buildOlLayer(sl.url, sl.label);
                map.addLayer(olLayer);
                layerRegistry[sl.taskId] = olLayer;
            }
        }
    }

    const stopWatcher = watch(
        () => store.state.geodata.stagingLayers as any[],
        (layers) => syncLayers(layers),
        { immediate: true, deep: false }
    );

    onBeforeUnmount(() => {
        stopWatcher();
        for (const olLayer of Object.values(layerRegistry)) {
            map.removeLayer(olLayer);
        }
    });

    return {
        /** Programmatically remove a staging layer both from OL map and the store */
        remove(taskId: string) {
            store.dispatch('geodata/removeStagingLayer', taskId);
        },
    };
}
