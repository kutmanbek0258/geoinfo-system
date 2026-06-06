import { ref, watch, type Ref } from 'vue';
import * as Cesium from 'cesium';
// @ts-ignore
import CesiumMVTImageryProvider from 'cesium-mvt-imagery-provider';

export function useCesiumMvt(
  viewer: Ref<Cesium.Viewer | null>,
  projectId: Ref<string>,
  selectedFeatureId: Ref<string | null>,
  hiddenFeatureIds: Ref<Set<string>>,
  isGeometryEditMode: Ref<boolean>
) {
  let pointsMvtLayer: Cesium.ImageryLayer | null = null;
  let linesMvtLayer: Cesium.ImageryLayer | null = null;
  let polygonsMvtLayer: Cesium.ImageryLayer | null = null;

  const cacheBuster = ref(Date.now());

  const cesiumMvtStyleFunction = (feature: any) => {
    const charProp = feature.properties?.characteristics;
    let characteristics = charProp;
    if (typeof charProp === 'string' && charProp.length > 0 && charProp.startsWith('{')) {
      try {
        characteristics = JSON.parse(charProp);
      } catch (e) {
        characteristics = feature.properties;
      }
    } else if (!characteristics) {
      characteristics = feature.properties || {};
    }

    const style = characteristics?.style || {};
    return {
      strokeStyle: typeof style.line?.color === 'string' ? style.line.color : '#00FF00',
      fillStyle: typeof style.poly?.fillColor === 'string' ? style.poly.fillColor : 'rgba(0, 255, 0, 0.3)',
      lineWidth: typeof style.line?.width === 'number' ? style.line.width : 2
    };
  };

  const raiseMvtLayersToTop = () => {
    const v = viewer.value;
    if (!v) return;
    [polygonsMvtLayer, linesMvtLayer, pointsMvtLayer].forEach(layer => {
      if (layer && v.imageryLayers.contains(layer)) {
        v.imageryLayers.raiseToTop(layer);
      }
    });
  };

  const initMvtLayers = (newProjectId: string) => {
    const v = viewer.value;
    if (!v) return;

    if (pointsMvtLayer) v.imageryLayers.remove(pointsMvtLayer);
    if (linesMvtLayer) v.imageryLayers.remove(linesMvtLayer);
    if (polygonsMvtLayer) v.imageryLayers.remove(polygonsMvtLayer);

    const baseUrl = window.location.origin;
    const mvtOptions = {
      onRenderFeature: (feature: any) => {
        if (isGeometryEditMode.value && feature.properties.id === selectedFeatureId.value) return false;
        if (feature.properties.id && hiddenFeatureIds.value.has(feature.properties.id)) return false;

        const charProp = feature.properties.characteristics;
        let char = charProp;
        if (typeof charProp === 'string' && charProp.startsWith('{')) {
          try { char = JSON.parse(charProp); } catch (e) { char = feature.properties; }
        } else if (!char) {
          char = feature.properties;
        }
        return char?.visible !== false && char?.isVisible !== false;
      },
      style: cesiumMvtStyleFunction,
      tilingScheme: new Cesium.WebMercatorTilingScheme(),
      tileWidth: 512,
      tileHeight: 512,
    };

    polygonsMvtLayer = v.imageryLayers.addImageryProvider(new CesiumMVTImageryProvider({
      ...mvtOptions,
      urlTemplate: `${baseUrl}/tiles/geodata.mvt_project_polygons/{z}/{x}/{y}.pbf?project_id_param=${newProjectId}&t=${cacheBuster.value}`,
      layerName: 'geodata.mvt_project_polygons'
    }));

    linesMvtLayer = v.imageryLayers.addImageryProvider(new CesiumMVTImageryProvider({
      ...mvtOptions,
      urlTemplate: `${baseUrl}/tiles/geodata.mvt_project_multilines/{z}/{x}/{y}.pbf?project_id_param=${newProjectId}&t=${cacheBuster.value}`,
      layerName: 'geodata.mvt_project_multilines'
    }));

    pointsMvtLayer = v.imageryLayers.addImageryProvider(new CesiumMVTImageryProvider({
      ...mvtOptions,
      urlTemplate: `${baseUrl}/tiles/geodata.mvt_project_points/{z}/{x}/{y}.pbf?project_id_param=${newProjectId}&t=${cacheBuster.value}`,
      layerName: 'geodata.mvt_project_points'
    }));

    raiseMvtLayersToTop();
  };

  const refreshMvtSources = () => {
    cacheBuster.value = Date.now();
    if (projectId.value) {
      initMvtLayers(projectId.value);
    }
  };

  watch(hiddenFeatureIds, refreshMvtSources, { deep: true });
  watch(projectId, (newId) => { if (newId) initMvtLayers(newId); });

  return {
    refreshMvtSources,
    initMvtLayers,
    raiseMvtLayersToTop
  };
}
