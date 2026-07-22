import { watch, onBeforeUnmount, ref } from 'vue';
import { useStore } from 'vuex';
import type { Ref } from 'vue';
import type OlMap from 'ol/Map';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import MVT from 'ol/format/MVT';
import { Fill, Stroke, Style, Circle as CircleStyle } from 'ol/style';
import TileLayer from 'ol/layer/Tile';
import XYZ from 'ol/source/XYZ';
import type { Layer } from 'ol/layer';

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

type StagingLayerMeta = {
    taskId: string;
    type: string;
    url: string;
    s3Url?: string;
    interpolation?: string;
    colormap?: string;
    styleId?: string | null;
    colormapId?: string | null;
    rescaleMin?: number | null;
    rescaleMax?: number | null;
    label: string;
};

function buildTiTilerUrl(
    s3Url: string,
    interpolation: string,
    colormap?: string,
    colormapId?: string | null,
    rescaleMin?: number | null,
    rescaleMax?: number | null
): string {
    let url = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(s3Url)}`;
    if (colormapId) {
        url += `&colormap_name=${colormapId}&resampling=${interpolation}`;
        if (rescaleMin !== undefined && rescaleMin !== null && rescaleMax !== undefined && rescaleMax !== null) {
            url += `&rescale=${rescaleMin},${rescaleMax}`;
        }
    } else {
        url += `&resampling=${interpolation}`;
        if (colormap) {
            url += `&colormap=${encodeURIComponent(colormap)}`;
        }
    }
    return url;
}

/**
 * Composable that watches `geodata.stagingLayers` in the Vuex store and
 * synchronises OpenLayers layer instances on the provided map.
 * Accepts either a raw OlMap or a Ref<OlMap|null> so it can be used in setup()
 * before the map is mounted (will start syncing once the ref becomes non-null).
 */
export function useStagingLayers(mapOrRef: OlMap | Ref<OlMap | null>) {
    const store = useStore();
    const layerRegistry: Record<string, Layer> = {};
    const visibleStagingLayerIds = ref<Record<string, boolean>>({});

    function getMap(): OlMap | null {
        return (mapOrRef && typeof (mapOrRef as Ref<OlMap | null>).value !== 'undefined')
            ? (mapOrRef as Ref<OlMap | null>).value
            : (mapOrRef as OlMap);
    }

    function buildVectorLayer(url: string, label: string): VectorTileLayer {
        return new VectorTileLayer({
            source: new VectorTileSource({ format: new MVT(), url }),
            style: stagingStyle,
            properties: { isStagingLayer: true, layerType: 'VECTOR', label },
            zIndex: 100,
        });
    }

    function buildRasterLayer(url: string, label: string): TileLayer {
        return new TileLayer({
            source: new XYZ({
                url,
                transition: 0
            }),
            opacity: 0.85,
            properties: { isStagingLayer: true, layerType: 'RASTER', label },
            zIndex: 90,
        });
    }

    function syncLayers(stagingLayers: StagingLayerMeta[]) {
        const olMap = getMap();
        if (!olMap) return;

        const incomingIds = new Set(stagingLayers.map(l => l.taskId));

        // Remove layers that are no longer in state
        for (const taskId of Object.keys(layerRegistry)) {
            if (!incomingIds.has(taskId)) {
                olMap.removeLayer(layerRegistry[taskId]);
                delete layerRegistry[taskId];
                delete visibleStagingLayerIds.value[taskId];
            }
        }

        // Add new layers
        for (const sl of stagingLayers) {
            const existingLayer = layerRegistry[sl.taskId];
            if (existingLayer) {
                if (sl.type === 'RASTER' && sl.s3Url) {
                    const currentSource = (existingLayer as any).getSource() as XYZ;
                    const newTileUrl = buildTiTilerUrl(sl.s3Url, sl.interpolation || 'bilinear', sl.colormap, sl.colormapId, sl.rescaleMin, sl.rescaleMax);
                    if (currentSource) {
                        const urls = currentSource.getUrls();
                        if (!urls || !urls.includes(newTileUrl)) {
                            currentSource.setUrl(newTileUrl);
                        }
                    }
                }
                continue; // already mounted
            }
            if (visibleStagingLayerIds.value[sl.taskId] === undefined) {
                visibleStagingLayerIds.value[sl.taskId] = true;
            }
            const isVisible = visibleStagingLayerIds.value[sl.taskId] !== false;

            if (sl.type === 'VECTOR') {
                const olLayer = buildVectorLayer(sl.url, sl.label);
                olLayer.setVisible(isVisible);
                olMap.addLayer(olLayer);
                layerRegistry[sl.taskId] = olLayer;
             } else if (sl.type === 'RASTER') {
                const tileUrl = sl.s3Url ? buildTiTilerUrl(sl.s3Url, sl.interpolation || 'bilinear', sl.colormap, sl.colormapId, sl.rescaleMin, sl.rescaleMax) : sl.url;
                const olLayer = buildRasterLayer(tileUrl, sl.label);
                olLayer.setVisible(isVisible);
                olMap.addLayer(olLayer);
                layerRegistry[sl.taskId] = olLayer;
            }
        }
    }

    /** Toggle map layer visibility by taskId */
    function setVisible(taskId: string, visible: boolean) {
        visibleStagingLayerIds.value[taskId] = visible;
        const layer = layerRegistry[taskId];
        if (layer) layer.setVisible(visible);
    }

    // Watch both staging layers AND map ref (for the case the map mounts after the composable)
    const stopStagingWatcher = watch(
        () => store.state.geodata.stagingLayers as StagingLayerMeta[],
        (layers) => syncLayers(layers),
        { immediate: true, deep: false }
    );

    // If a Ref<OlMap> was passed, also watch for map becoming available
    let stopMapWatcher: (() => void) | undefined;
    if ((mapOrRef as Ref<OlMap | null>).value !== undefined) {
        stopMapWatcher = watch(
            () => (mapOrRef as Ref<OlMap | null>).value,
            (newMap) => {
                if (newMap) syncLayers(store.state.geodata.stagingLayers);
            },
            { immediate: false }
        );
    }

    onBeforeUnmount(() => {
        stopStagingWatcher();
        stopMapWatcher?.();
        const olMap = getMap();
        if (olMap) {
            for (const olLayer of Object.values(layerRegistry)) {
                olMap.removeLayer(olLayer);
            }
        }
    });

    return {
        /** Toggle visibility of a staging layer on the OL map */
        setVisible,
        visibleStagingLayerIds,
        /** Remove a staging layer from both the OL map and the store */
        remove(taskId: string) {
            store.dispatch('geodata/removeStagingLayer', taskId);
        },
    };
}
