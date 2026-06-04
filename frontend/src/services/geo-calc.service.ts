import { getArea, getDistance } from 'ol/sphere';
import type { LineString, Polygon } from 'ol/geom';
import { toLonLat } from 'ol/proj';

/**
 * Service for geospatial calculations and formatting.
 * Handles explicit transformation from map projection to WGS84 for accuracy.
 */
class GeoCalcService {
  /**
   * Calculates the 3D distance between two points in EPSG:3857.
   */
  calculateDistance(c1: number[], c2: number[], useAltitude: boolean = false): number {
    if (!c1 || !c2) return 0;
    const p1 = toLonLat([c1[0], c1[1]]);
    const p2 = toLonLat([c2[0], c2[1]]);
    const d_h = getDistance(p1, p2);
    
    if (!useAltitude || c1.length < 3 || c2.length < 3) {
      return d_h;
    }
    const z1 = c1[2] || 0;
    const z2 = c2[2] || 0;
    const d_v = z2 - z1;
    return Math.sqrt(d_h * d_h + d_v * d_v);
  }

  /**
   * Format length of a LineString.
   */
  formatLength(line: LineString, useAltitude: boolean = false): string {
    const coords = line.getCoordinates();
    let totalLength = 0;
    for (let i = 0; i < coords.length - 1; i++) {
      totalLength += this.calculateDistance(coords[i], coords[i + 1], useAltitude);
    }
    return this.formatDistance(totalLength);
  }

  /**
   * Formats a raw distance value into a readable string.
   */
  formatDistance(length: number): string {
    if (length >= 1000) {
      return (length / 1000).toFixed(2) + ' ' + 'km';
    }
    return length.toFixed(2) + ' ' + 'm';
  }

  /**
   * Format area of a Polygon using geodesic calculation.
   * Switches units: m² -> ha -> km² for better readability.
   */
  formatArea(polygon: Polygon): string {
    // getArea with projection calculates geodesic area in square meters
    const area = Math.abs(getArea(polygon, { projection: 'EPSG:3857' }));
    
    if (area >= 1000000) {
      return (area / 1000000).toFixed(2) + ' ' + 'km²';
    }
    if (area >= 10000) {
      return (area / 10000).toFixed(2) + ' ' + 'ha';
    }
    return area.toFixed(2) + ' ' + 'm²';
  }
}

export default new GeoCalcService();
