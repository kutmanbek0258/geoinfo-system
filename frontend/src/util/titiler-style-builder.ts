import type { ColorMapEntry } from '@/types/api';

export function hexToRgb(hex: string) {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : { r: 0, g: 0, b: 0 };
}

export function buildTiTilerColormap(config: ColorMapEntry[]): string {
  if (!config || config.length === 0) return "";

  // Filter out NoData placeholder values (typically <= -999)
  const validEntries = config.filter(e => e.quantity > -999);
  if (validEntries.length === 0) return "";

  if (validEntries.length < 2) {
    // If only 1 style point, render a discrete map mapping that value to color
    const colormapObj: Record<string, string> = {};
    validEntries.forEach(entry => {
      colormapObj[entry.quantity.toString()] = entry.color;
    });
    return JSON.stringify(colormapObj);
  }

  // Sort style points by quantity
  const sorted = [...validEntries].sort((a, b) => a.quantity - b.quantity);

  const intervals = [];
  for (let i = 0; i < sorted.length - 1; i++) {
    const start = sorted[i];
    const end = sorted[i + 1];
    const rgb = hexToRgb(start.color);
    const alpha = Math.round((start.opacity !== undefined ? start.opacity : 1.0) * 255);
    intervals.push([[start.quantity, end.quantity], [rgb.r, rgb.g, rgb.b, alpha]]);
  }

  return JSON.stringify(intervals);
}

export function getExtentFromGeometry(geom: any): [number, number, number, number] | null {
  if (!geom || !geom.coordinates) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  const traverse = (coords: any) => {
    if (typeof coords[0] === 'number') {
      const [x, y] = coords;
      if (x < minX) minX = x;
      if (y < minY) minY = y;
      if (x > maxX) maxX = x;
      if (y > maxY) maxY = y;
    } else {
      for (const sub of coords) {
        traverse(sub);
      }
    }
  };

  traverse(geom.coordinates);
  if (minX === Infinity || minY === Infinity) return null;
  return [minX, minY, maxX, maxY];
}

export function buildTiTilerStyleParams(
  style: any,
  colormapId?: string | null,
  resampling?: string | null
): string {
  let params = "";
  const res = resampling || 'nearest';
  if (colormapId) {
    params = `&colormap_name=${colormapId}&resampling=${res}`;
  } else {
    params = `&resampling=${res}`;
    if (style && style.config) {
      const colormapStr = buildTiTilerColormap(style.config);
      if (colormapStr) {
        params += "&colormap=" + encodeURIComponent(colormapStr);
      }
    }
  }
  return params;
}

