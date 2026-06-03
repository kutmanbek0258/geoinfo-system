import { getLength, getArea, getDistance } from 'ol/sphere';
import type { LineString, Polygon } from 'ol/geom';

/**
 * Service for geospatial calculations and formatting.
 */
class GeoCalcService {
  /**
   * Format length of a LineString.
   * @param line The line geometry.
   * @param useAltitude Whether to take altitude (Z coordinate) into account for 3D distance.
   * @returns Formatted string (e.g., "1.5 km" or "500 m").
   */
  formatLength(line: LineString, useAltitude: boolean = false): string {
    let length = 0;
    const coordinates = line.getCoordinates();
    
    if (useAltitude) {
      // Calculate 3D distance
      for (let i = 0; i < coordinates.length - 1; i++) {
        const c1 = coordinates[i];
        const c2 = coordinates[i + 1];
        
        // Horizontal distance (geodesic)
        const d_h = getDistance(c1, c2);
        
        // Vertical distance
        const z1 = c1[2] || 0;
        const z2 = c2[2] || 0;
        const d_v = z2 - z1;
        
        // 3D distance using Pythagorean theorem
        length += Math.sqrt(d_h * d_h + d_v * d_v);
      }
    } else {
      // Standard 2D geodesic distance
      length = getLength(line);
    }

    let output: string;
    if (length > 1000) {
      output = Math.round((length / 1000) * 100) / 100 + ' ' + 'km';
    } else {
      output = Math.round(length * 100) / 100 + ' ' + 'm';
    }
    return output;
  }

  /**
   * Format area of a Polygon.
   * @param polygon The polygon geometry.
   * @returns Formatted string (e.g., "1.5 km²" or "500 m²").
   */
  formatArea(polygon: Polygon): string {
    const area = getArea(polygon);
    let output: string;
    if (area > 1000000) {
      output = Math.round((area / 1000000) * 100) / 100 + ' ' + 'km²';
    } else {
      output = Math.round(area * 100) / 100 + ' ' + 'm²';
    }
    return output;
  }
}

export default new GeoCalcService();
