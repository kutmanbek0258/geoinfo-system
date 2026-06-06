import { ref, watch, type Ref } from 'vue';
import * as Cesium from 'cesium';
import { Polygon as OLPolygon } from 'ol/geom';
import geoCalcService from '@/services/geo-calc.service';

export function useCesiumInteractions(
  viewer: Ref<Cesium.Viewer | null>,
  drawMode: Ref<'Point' | 'MultiLineString' | 'Polygon' | null>,
  measureMode: Ref<'length' | 'area' | null>,
  isBufferMode: Ref<boolean>,
  bufferDistance: Ref<number>,
  sampleHeights: (points: Cesium.Cartesian3[]) => Promise<number[][]>
) {
  const measurePoints = ref<Cesium.Cartesian3[]>([]);
  const measureEntities = ref<Cesium.Entity[]>([]);
  const measureTooltips = ref<{ position: Cesium.Cartesian3, text: string }[]>([]);
  let measureHandler: Cesium.ScreenSpaceEventHandler | null = null;

  const bufferCenterCoords = ref<number[]>([0, 0]);
  const bufferSourceEntity = ref<Cesium.Entity | null>(null);
  const bufferPreviewEntity = ref<Cesium.Entity | null>(null);
  let bufferHandler: Cesium.ScreenSpaceEventHandler | null = null;

  const clearMeasurements = () => {
    const v = viewer.value;
    if (!v) return;
    measureEntities.value.forEach(e => v.entities.remove(e));
    measureEntities.value = [];
    measurePoints.value = [];
    measureTooltips.value = [];

    if (bufferSourceEntity.value) { v.entities.remove(bufferSourceEntity.value); bufferSourceEntity.value = null; }
    if (bufferPreviewEntity.value) { v.entities.remove(bufferPreviewEntity.value); bufferPreviewEntity.value = null; }
    bufferCenterCoords.value = [0, 0];
  };

  const updateBufferPreview = () => {
    const v = viewer.value;
    if (!v || !bufferCenterCoords.value[0]) return;
    if (bufferPreviewEntity.value) v.entities.remove(bufferPreviewEntity.value);

    bufferPreviewEntity.value = v.entities.add({
      position: Cesium.Cartesian3.fromDegrees(bufferCenterCoords.value[0], bufferCenterCoords.value[1]),
      ellipse: {
        semiMinorAxis: bufferDistance.value || 1,
        semiMajorAxis: bufferDistance.value || 1,
        material: new Cesium.ColorMaterialProperty(Cesium.Color.BLUE.withAlpha(0.2)),
        outline: true,
        outlineColor: Cesium.Color.BLUE,
        outlineWidth: 2,
        heightReference: Cesium.HeightReference.RELATIVE_TO_GROUND
      }
    });
  };

  watch(measureMode, (newMode) => {
    const v = viewer.value;
    if (!v) return;
    if (measureHandler) { measureHandler.destroy(); measureHandler = null; }
    clearMeasurements();
    if (!newMode) return;

    measureHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
    measureHandler.setInputAction(async (click: any) => {
      const position = v.scene.pickPosition(click.position);
      if (Cesium.defined(position)) {
        measurePoints.value.push(position);
        measureEntities.value.push(v.entities.add({
          position: position,
          point: { 
            pixelSize: 8, 
            color: Cesium.Color.YELLOW, 
            outlineColor: Cesium.Color.BLACK, 
            outlineWidth: 2, 
            disableDepthTestDistance: Number.POSITIVE_INFINITY 
          }
        }));

        if (newMode === 'length' && measurePoints.value.length > 1) {
          const p1 = measurePoints.value[measurePoints.value.length - 2];
          const p2 = measurePoints.value[measurePoints.value.length - 1];
          
          const carto1 = Cesium.Cartographic.fromCartesian(p1);
          const carto2 = Cesium.Cartographic.fromCartesian(p2);
          
          const dist = geoCalcService.calculateDistance(
            [Cesium.Math.toDegrees(carto1.longitude), Cesium.Math.toDegrees(carto1.latitude), carto1.height],
            [Cesium.Math.toDegrees(carto2.longitude), Cesium.Math.toDegrees(carto2.latitude), carto2.height],
            true
          );
          measureTooltips.value.push({ position: position, text: geoCalcService.formatDistance(dist) });
        }
      }
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

    measureEntities.value.push(v.entities.add({
      polyline: {
        positions: new Cesium.CallbackProperty(() => measurePoints.value, false) as any,
        width: 2,
        material: new Cesium.PolylineDashMaterialProperty({ color: Cesium.Color.YELLOW }),
        clampToGround: true
      },
      polygon: newMode === 'area' ? {
        hierarchy: new Cesium.CallbackProperty(() => new Cesium.PolygonHierarchy(measurePoints.value), false) as any,
        material: new Cesium.ColorMaterialProperty(Cesium.Color.YELLOW.withAlpha(0.3))
      } : undefined
    }));

    measureHandler.setInputAction(async () => {
      if (newMode === 'area' && measurePoints.value.length >= 3) {
        const coords = await sampleHeights(measurePoints.value);
        const polygon = new OLPolygon([coords.map(c => [c[0], c[1]])]);
        const center = Cesium.BoundingSphere.fromPoints(measurePoints.value).center;
        measureTooltips.value.push({ position: center, text: `Total Area: ${geoCalcService.formatArea(polygon)}` });
      }
      if (measureHandler) { measureHandler.destroy(); measureHandler = null; }
    }, Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK);
  });

  watch(isBufferMode, (active) => {
    const v = viewer.value;
    if (!v) return;
    if (bufferHandler) { bufferHandler.destroy(); bufferHandler = null; }
    clearMeasurements();
    if (!active) return;

    bufferHandler = new Cesium.ScreenSpaceEventHandler(v.canvas);
    bufferHandler.setInputAction((click: any) => {
      const position = v.scene.pickPosition(click.position);
      if (Cesium.defined(position)) {
        const carto = Cesium.Cartographic.fromCartesian(position);
        bufferCenterCoords.value = [Cesium.Math.toDegrees(carto.longitude), Cesium.Math.toDegrees(carto.latitude)];
        if (bufferSourceEntity.value) v.entities.remove(bufferSourceEntity.value);
        bufferSourceEntity.value = v.entities.add({
          position: position,
          point: { pixelSize: 10, color: Cesium.Color.BLUE, outlineColor: Cesium.Color.WHITE, outlineWidth: 2, disableDepthTestDistance: Number.POSITIVE_INFINITY }
        });
        updateBufferPreview();
      }
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK);
  });

  watch(bufferDistance, updateBufferPreview);

  return {
    measurePoints,
    measureEntities,
    measureTooltips,
    bufferCenterCoords,
    bufferSourceEntity,
    bufferPreviewEntity,
    clearMeasurements
  };
}
