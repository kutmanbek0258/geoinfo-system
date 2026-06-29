import { watch, onBeforeUnmount, ref, type Ref } from 'vue';
import { useStore } from 'vuex';
import * as Cesium from 'cesium';
// @ts-ignore
import CesiumMVTImageryProvider from 'cesium-mvt-imagery-provider';

type StagingLayerMeta = {
    taskId: string;
    type: string;
    url: string;
    s3Url?: string;
    interpolation?: string;
    colormap?: string;
    styleId?: string | null;
    colormapId?: string | null;
    label: string;
};

function buildTiTilerUrl(s3Url: string, interpolation: string, colormap?: string, colormapId?: string | null): string {
    let url = `/raster/cog/cog/tiles/WebMercatorQuad/{z}/{x}/{y}?url=${encodeURIComponent(s3Url)}`;
    if (colormapId) {
        url += `&colormap_name=${colormapId}&resampling=${interpolation}`;
    } else {
        url += `&resampling=${interpolation}`;
        if (colormap) {
            url += `&colormap=${encodeURIComponent(colormap)}`;
        }
    }
    return url;
}

export function useCesiumStagingLayers(viewer: Ref<Cesium.Viewer | null>) {
    const store = useStore();
    const layerRegistry: Record<string, Cesium.ImageryLayer> = {};
    const visibleStagingLayerIds = ref<Record<string, boolean>>({});

    function syncLayers(stagingLayers: StagingLayerMeta[]) {
        const v = viewer.value;
        if (!v) return;

        const incomingIds = new Set(stagingLayers.map(l => l.taskId));

        // Remove layers that are no longer in state
        for (const taskId of Object.keys(layerRegistry)) {
            if (!incomingIds.has(taskId)) {
                v.imageryLayers.remove(layerRegistry[taskId]);
                delete layerRegistry[taskId];
                delete visibleStagingLayerIds.value[taskId];
            }
        }

        // Add or update layers
        for (const sl of stagingLayers) {
            const existingLayer = layerRegistry[sl.taskId];
            if (existingLayer) {
                // If it is RASTER and url changes
                if (sl.type === 'RASTER' && sl.s3Url) {
                    const newTileUrl = buildTiTilerUrl(sl.s3Url, sl.interpolation || 'bilinear', sl.colormap, sl.colormapId);
                    const currentProvider = existingLayer.imageryProvider as Cesium.UrlTemplateImageryProvider;
                    if (currentProvider && (currentProvider as any).urlTemplate !== newTileUrl) {
                        v.imageryLayers.remove(existingLayer);
                        
                        const provider = new Cesium.UrlTemplateImageryProvider({
                            url: newTileUrl
                        });
                        const layer = v.imageryLayers.addImageryProvider(provider);
                        layer.show = visibleStagingLayerIds.value[sl.taskId] !== false;
                        layerRegistry[sl.taskId] = layer;
                    }
                }
                continue;
            }

            if (visibleStagingLayerIds.value[sl.taskId] === undefined) {
                visibleStagingLayerIds.value[sl.taskId] = true;
            }
            const isVisible = visibleStagingLayerIds.value[sl.taskId] !== false;

            // Create new layer
            if (sl.type === 'VECTOR') {
                const baseUrl = window.location.origin;
                let urlTemplate = sl.url.startsWith('http') ? sl.url : `${baseUrl}${sl.url}`;
                if (!urlTemplate.includes('{z}')) {
                    urlTemplate = `${urlTemplate}/{z}/{x}/{y}.pbf`;
                }

                const provider = new CesiumMVTImageryProvider({
                    urlTemplate: urlTemplate as any,
                    layerName: 'geodata.staging_layer',
                    style: () => ({
                        strokeStyle: '#ff6b35',
                        fillStyle: 'rgba(255, 107, 53, 0.15)',
                        lineWidth: 2.5
                    }),
                    tilingScheme: new Cesium.WebMercatorTilingScheme(),
                    tileWidth: 512,
                    tileHeight: 512,
                } as any);
                const layer = v.imageryLayers.addImageryProvider(provider);
                layer.show = isVisible;
                layerRegistry[sl.taskId] = layer;
                visibleStagingLayerIds.value[sl.taskId] = isVisible;
            } else if (sl.type === 'RASTER') {
                const tileUrl = sl.s3Url ? buildTiTilerUrl(sl.s3Url, sl.interpolation || 'bilinear', sl.colormap, sl.colormapId) : sl.url;
                const provider = new Cesium.UrlTemplateImageryProvider({
                    url: tileUrl
                });
                const layer = v.imageryLayers.addImageryProvider(provider);
                layer.show = isVisible;
                layerRegistry[sl.taskId] = layer;
                visibleStagingLayerIds.value[sl.taskId] = isVisible;
            }
        }
        raiseStagingLayersToTop();
    }

    function raiseStagingLayersToTop() {
        const v = viewer.value;
        if (!v) return;

        const layers = store.state.geodata.stagingLayers as StagingLayerMeta[];
        
        // First raise rasters
        for (const sl of layers) {
            if (sl.type === 'RASTER') {
                const layer = layerRegistry[sl.taskId];
                if (layer && v.imageryLayers.contains(layer)) {
                    v.imageryLayers.raiseToTop(layer);
                }
            }
        }

        // Then raise vectors
        for (const sl of layers) {
            if (sl.type === 'VECTOR') {
                const layer = layerRegistry[sl.taskId];
                if (layer && v.imageryLayers.contains(layer)) {
                    v.imageryLayers.raiseToTop(layer);
                }
            }
        }
    }

    function setVisible(taskId: string, visible: boolean) {
        visibleStagingLayerIds.value[taskId] = visible;
        const layer = layerRegistry[taskId];
        if (layer) {
            layer.show = visible;
        }
    }

    const stopStagingWatcher = watch(
        () => store.state.geodata.stagingLayers as StagingLayerMeta[],
        (layers) => syncLayers(layers),
        { immediate: true, deep: false }
    );

    watch(
        () => viewer.value,
        (newViewer) => {
            if (newViewer) {
                syncLayers(store.state.geodata.stagingLayers);
            }
        }
    );

    onBeforeUnmount(() => {
        stopStagingWatcher();
        const v = viewer.value;
        if (v) {
            for (const key in layerRegistry) {
                v.imageryLayers.remove(layerRegistry[key]);
            }
        }
    });

    return {
        setVisible,
        visibleStagingLayerIds,
        raiseStagingLayersToTop,
        remove(taskId: string) {
            store.dispatch('geodata/removeStagingLayer', taskId);
        }
    };
}
