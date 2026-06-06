import { ref, watch, type Ref } from 'vue';
import { Map } from 'ol';
import { Style } from 'ol/style';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import MVT from 'ol/format/MVT';
import { parseStyle } from '@/util/style.util';

export function useOlMvt(
  map: Ref<Map | null>,
  projectId: Ref<string>,
  selectedFeatureId: Ref<string | null>,
  hiddenFeatureIds: Ref<Set<string>>,
  isGeometryEditMode: Ref<boolean>
) {
  let pointsTileSource: VectorTileSource | null = null;
  let linesTileSource: VectorTileSource | null = null;
  let polygonsTileSource: VectorTileSource | null = null;

  let pointsTileLayer: VectorTileLayer | null = null;
  let linesTileLayer: VectorTileLayer | null = null;
  let polygonsTileLayer: VectorTileLayer | null = null;

  let characteristicsCache = new WeakMap();
  const cacheBuster = ref(Date.now());

  const vectorTileStyleFunction = (feature: any) => {
    const id = feature.get('id');
    
    if (isGeometryEditMode.value && id === selectedFeatureId.value) {
      return new Style();
    }

    if (id && hiddenFeatureIds.value.has(id)) {
      return new Style();
    }

    let characteristics = characteristicsCache.get(feature);
    if (!characteristics) {
      const charProp = feature.get('characteristics');
      if (charProp) {
        if (typeof charProp === 'string' && (charProp.startsWith('{') || charProp.startsWith('['))) {
          try {
            characteristics = JSON.parse(charProp);
          } catch (e) {
            characteristics = feature.getProperties();
          }
        } else {
          characteristics = charProp;
        }
      } else {
        characteristics = feature.getProperties();
      }
      
      if (characteristics && typeof characteristics === 'object') {
        characteristicsCache.set(feature, characteristics);
      }
    }

    const isVisibleInTile = characteristics?.visible !== false && characteristics?.isVisible !== false;
    if (!isVisibleInTile) {
      return new Style();
    }

    const name = feature.get('name');
    return parseStyle(characteristics, name);
  };

  const initMvtLayers = (newProjectId: string) => {
    const m = map.value;
    if (!m) return;

    if (pointsTileLayer) m.removeLayer(pointsTileLayer);
    if (linesTileLayer) m.removeLayer(linesTileLayer);
    if (polygonsTileLayer) m.removeLayer(polygonsTileLayer);

    const createSource = (layerName: string) => new VectorTileSource({
      format: new MVT(),
      maxZoom: 20,
      tileUrlFunction: (tileCoord) => {
        return `/tiles/${layerName}/${tileCoord[0]}/${tileCoord[1]}/${tileCoord[2]}.pbf?project_id_param=${newProjectId}&t=${cacheBuster.value}`;
      }
    });

    pointsTileSource = createSource('geodata.mvt_project_points');
    linesTileSource = createSource('geodata.mvt_project_multilines');
    polygonsTileSource = createSource('geodata.mvt_project_polygons');

    polygonsTileLayer = new VectorTileLayer({
      source: polygonsTileSource,
      style: vectorTileStyleFunction,
      zIndex: 98,
      renderMode: 'vector'
    });

    linesTileLayer = new VectorTileLayer({
      source: linesTileSource,
      style: vectorTileStyleFunction,
      zIndex: 99,
      renderMode: 'vector'
    });

    pointsTileLayer = new VectorTileLayer({
      source: pointsTileSource,
      style: vectorTileStyleFunction,
      zIndex: 100,
      renderMode: 'vector'
    });

    m.addLayer(polygonsTileLayer);
    m.addLayer(linesTileLayer);
    m.addLayer(pointsTileLayer);
  };

  const refreshMvtSources = () => {
    characteristicsCache = new WeakMap();
    if (pointsTileSource) pointsTileSource.refresh();
    if (linesTileSource) linesTileSource.refresh();
    if (polygonsTileSource) polygonsTileSource.refresh();
  };

  watch(hiddenFeatureIds, () => {
    if (pointsTileLayer) pointsTileLayer.changed();
    if (linesTileLayer) linesTileLayer.changed();
    if (polygonsTileLayer) polygonsTileLayer.changed();
  }, { deep: true });

  watch(projectId, (newId) => {
    if (newId) {
      initMvtLayers(newId);
    }
  });

  return {
    refreshMvtSources,
    initMvtLayers,
    pointsTileLayer,
    linesTileLayer,
    polygonsTileLayer
  };
}
