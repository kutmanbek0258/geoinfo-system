import { Style, Icon, Stroke, Fill, Circle, Text } from 'ol/style';

/**
 * Parses a style object from the characteristics field and returns an OpenLayers Style.
 * @param characteristics The characteristics field containing style information.
 * @param name Optional name for labeling.
 * @returns An OpenLayers Style object.
 */
export const parseStyle = (characteristics: any, name?: string): Style => {
  const styleData = characteristics?.style;
  const style = new Style();

  if (!styleData) {
    // Default styles if no style data is provided
    style.setStroke(new Stroke({ color: '#3399CC', width: 2 }));
    style.setFill(new Fill({ color: 'rgba(255,255,255,0.4)' }));
    style.setImage(new Circle({
      radius: 5,
      fill: new Fill({ color: '#3399CC' }),
      stroke: new Stroke({ color: '#fff', width: 1 }),
    }));
    return style;
  }

  // 1. Icon Style (for Points)
  if (styleData.icon) {
    const iconData = styleData.icon;
    style.setImage(new Icon({
      src: iconData.url,
      scale: iconData.scale || 1,
      rotation: iconData.heading ? (iconData.heading * Math.PI) / 180 : 0,
      anchor: iconData.hotSpot ? [
        parseHotSpotValue(iconData.hotSpot.x, iconData.hotSpot.xunits),
        parseHotSpotValue(iconData.hotSpot.y, iconData.hotSpot.yunits)
      ] : [0.5, 0.5],
      anchorXUnits: iconData.hotSpot?.xunits === 'pixels' ? 'pixels' : 'fraction',
      anchorYUnits: iconData.hotSpot?.yunits === 'pixels' ? 'pixels' : 'fraction',
    }));
  } else {
    // Default circle for points without icon
    style.setImage(new Circle({
      radius: 6,
      fill: new Fill({ color: styleData.poly?.fillColor || '#3399CC' }),
      stroke: new Stroke({ color: styleData.line?.color || '#fff', width: styleData.line?.width || 1 }),
    }));
  }

  // 2. Line Style
  if (styleData.line) {
    style.setStroke(new Stroke({
      color: styleData.line.color || '#3399CC',
      width: styleData.line.width || 2,
    }));
  }

  // 3. Poly Style
  if (styleData.poly) {
    style.setFill(new Fill({
      color: styleData.poly.fillColor || 'rgba(255,255,255,0.4)',
    }));
  }

  // 4. Label Style
  if (name && styleData.label) {
    style.setText(new Text({
      text: name,
      scale: styleData.label.scale || 1,
      fill: new Fill({ color: styleData.label.color || '#000' }),
      stroke: new Stroke({ color: '#fff', width: 2 }),
      offsetY: -15,
    }));
  }

  return style;
};

/**
 * Parses KML hotSpot values which can be fractions or pixels.
 */
const parseHotSpotValue = (value: any, units: string): number => {
  const num = parseFloat(value);
  if (isNaN(num)) return 0.5;
  return num;
};
