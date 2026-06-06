import { ref, watch, shallowRef, type Ref } from 'vue';
import { Map, Feature, Overlay } from 'ol';
import { Draw, Snap } from 'ol/interaction';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';
import { Style, Stroke, Fill, Circle as CircleStyle } from 'ol/style';
import { LineString, Polygon as OLPolygon, Point } from 'ol/geom';
import { GeoJSON } from 'ol/format';
import { toLonLat } from 'ol/proj';
import * as turf from '@turf/turf';
import geoCalcService from '@/services/geo-calc.service';

export function useOlInteractions(
  map: Ref<Map | null>,
  drawMode: Ref<'Point' | 'MultiLineString' | 'Polygon' | null>,
  measureMode: Ref<'length' | 'area' | null>,
  isBufferMode: Ref<boolean>,
  bufferDistance: Ref<number>,
  points: Ref<any[]>,
  multilines: Ref<any[]>,
  polygons: Ref<any[]>
) {
  const geoJsonFormat = new GeoJSON();
  const tempSource = new VectorSource();
  const measureSource = new VectorSource();
  const snapSource = new VectorSource();

  let drawInteraction: Draw | null = null;
  let measureDraw: Draw | null = null;
  let snapInteraction: Snap | null = null;
  let bufferDrawInteraction: Draw | null = null;
  
  let bufferPreviewFeature: Feature | null = null;
  const bufferCenterCoords = ref<number[]>([0, 0]);
  const bufferSourceFeature = ref<any>(null);

  const activeMeasureTooltips = shallowRef<Overlay[]>([]);
  let measureTooltipElement: HTMLElement | null = null;
  let measureTooltip: Overlay | null = null;

  // --- Snap Source Updates ---
  const updateSnapSource = () => {
    const m = map.value;
    if (!m) return;
    snapSource.clear();
    
    const allObjects = [...points.value, ...multilines.value, ...polygons.value];
    const features: Feature[] = [];
    
    allObjects.forEach(obj => {
      if (obj.geom) {
        const read = geoJsonFormat.readFeatures(obj.geom, {
          dataProjection: 'EPSG:4326',
          featureProjection: 'EPSG:3857'
        });
        if (Array.isArray(read)) {
          features.push(...read);
        } else if (read) {
          features.push(read);
        }
      }
    });
    
    snapSource.addFeatures(features);
  };

  // --- Measurement Helpers ---
  const createMeasureTooltip = () => {
    const m = map.value;
    if (!m) return;

    if (measureTooltipElement) {
      measureTooltipElement.parentNode?.removeChild(measureTooltipElement);
    }
    measureTooltipElement = document.createElement('div');
    measureTooltipElement.className = 'ol-tooltip ol-tooltip-measure';
    measureTooltip = new Overlay({
      element: measureTooltipElement,
      offset: [0, -15],
      positioning: 'bottom-center',
      stopEvent: false,
      insertFirst: false,
    });
    m.addOverlay(measureTooltip);
    activeMeasureTooltips.value = [...activeMeasureTooltips.value, measureTooltip];
  };

  const clearMeasurements = () => {
    measureSource.clear();
    const m = map.value;
    if (m) {
      activeMeasureTooltips.value.forEach(ov => m.removeOverlay(ov));
    }
    activeMeasureTooltips.value = [];
  };

  // --- Buffer Logic ---
  const handleBufferPointerMove = (event: any) => {
    if (!bufferSourceFeature.value || !bufferCenterCoords.value) return;
    const center = bufferCenterCoords.value;
    const current = event.coordinate;
    const distance = geoCalcService.calculateDistance(center, current);
    bufferDistance.value = Math.max(1, Math.round(distance));
  };

  const updateBufferPreview = () => {
    if (!bufferSourceFeature.value || !bufferCenterCoords.value || !isBufferMode.value) return;
    try {
      const point = turf.point(toLonLat(bufferCenterCoords.value));
      const buffered = turf.buffer(point, bufferDistance.value, { units: 'meters' });
      const bufferedFeatures = geoJsonFormat.readFeatures(buffered, {
        dataProjection: 'EPSG:4326',
        featureProjection: 'EPSG:3857'
      });
      if (bufferPreviewFeature) {
        measureSource.removeFeature(bufferPreviewFeature);
      }
      bufferPreviewFeature = bufferedFeatures[0];
      measureSource.addFeature(bufferPreviewFeature);
    } catch (err) {
      console.error("Buffer calculation failed", err);
    }
  };

  const startBufferDrawing = () => {
    const m = map.value;
    if (!m) return;
    updateSnapSource();
    bufferDrawInteraction = new Draw({
      source: new VectorSource(),
      type: 'LineString',
      maxPoints: 2,
      style: new Style({
        image: new CircleStyle({
          radius: 5,
          stroke: new Stroke({ color: '#ffab00', width: 2 }),
          fill: new Fill({ color: 'rgba(255, 255, 255, 0.7)' }),
        }),
        stroke: new Stroke({ color: 'transparent' }) 
      }),
    });
    bufferDrawInteraction.on('drawstart', (evt) => {
      const sketch = evt.feature;
      const coords = (sketch.getGeometry() as LineString).getCoordinates();
      bufferCenterCoords.value = coords[0];
      bufferSourceFeature.value = new Feature({
        geometry: new Point(bufferCenterCoords.value)
      });
      m.on('pointermove', handleBufferPointerMove);
    });
    bufferDrawInteraction.on('drawend', () => {
      m.un('pointermove', handleBufferPointerMove);
      if (bufferPreviewFeature) {
        const finalBuffer = bufferPreviewFeature.clone();
        const finalCenter = new Feature(new Point(bufferCenterCoords.value));
        measureSource.addFeature(finalCenter);
        measureSource.addFeature(finalBuffer);
      }
      bufferPreviewFeature = null;
      bufferCenterCoords.value = [0, 0];
      bufferSourceFeature.value = null; 
    });
    m.addInteraction(bufferDrawInteraction);
    snapInteraction = new Snap({ source: snapSource });
    m.addInteraction(snapInteraction);
  };

  const stopBufferDrawing = () => {
    const m = map.value;
    if (m) {
      if (bufferDrawInteraction) m.removeInteraction(bufferDrawInteraction);
      if (snapInteraction) m.removeInteraction(snapInteraction);
      m.un('pointermove', handleBufferPointerMove);
    }
    if (bufferPreviewFeature) {
      measureSource.removeFeature(bufferPreviewFeature);
      bufferPreviewFeature = null;
    }
  };

  // --- Watchers for state changes ---
  watch(measureMode, (newMode) => {
    const m = map.value;
    if (!m) return;
    if (measureDraw) m.removeInteraction(measureDraw);
    if (snapInteraction) m.removeInteraction(snapInteraction);
    if (!newMode) return;

    updateSnapSource();
    const type = newMode === 'area' ? 'Polygon' : 'LineString';
    measureDraw = new Draw({
      source: measureSource,
      type: type,
      style: new Style({
        fill: new Fill({ color: 'rgba(255, 255, 255, 0.2)' }),
        stroke: new Stroke({ color: 'rgba(0, 0, 0, 0.5)', lineDash: [10, 10], width: 2 }),
        image: new CircleStyle({ radius: 5, stroke: new Stroke({ color: 'rgba(0, 0, 0, 0.7)' }), fill: new Fill({ color: 'rgba(255, 255, 255, 0.2)' }) }),
      }),
    });
    m.addInteraction(measureDraw);
    snapInteraction = new Snap({ source: snapSource });
    m.addInteraction(snapInteraction);
    createMeasureTooltip();

    measureDraw.on('drawstart', (evt) => {
      const sketch = evt.feature;
      let tooltipCoord: any;
      sketch.getGeometry()?.on('change', (ev) => {
        const geom = ev.target;
        let output: string = '';
        if (geom instanceof OLPolygon) {
          output = geoCalcService.formatArea(geom);
          tooltipCoord = geom.getInteriorPoint().getCoordinates();
        } else if (geom instanceof LineString) {
          output = geoCalcService.formatLength(geom);
          tooltipCoord = geom.getLastCoordinate();
        }
        if (measureTooltipElement) measureTooltipElement.innerHTML = output;
        measureTooltip?.setPosition(tooltipCoord);
      });
    });

    measureDraw.on('drawend', () => {
      if (measureTooltipElement) {
        measureTooltipElement.className = 'ol-tooltip ol-tooltip-static';
        measureTooltip?.setOffset([0, -7]);
      }
      measureTooltipElement = null;
      measureTooltip = null;
      createMeasureTooltip();
    });
  });

  watch(isBufferMode, (active) => {
    if (active) {
      startBufferDrawing();
    } else {
      stopBufferDrawing();
    }
  });

  watch([bufferDistance, bufferSourceFeature, isBufferMode], () => {
    if (isBufferMode.value) updateBufferPreview();
  });

  return {
    tempSource,
    measureSource,
    snapSource,
    clearMeasurements,
    updateSnapSource,
    bufferPreviewFeature,
    bufferSourceFeature,
    bufferCenterCoords,
  };
}
