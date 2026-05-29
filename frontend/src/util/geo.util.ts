/**
 * Ensures that the geometry object is in a 'Multi' format (MultiPoint, MultiLineString, MultiPolygon).
 * This is required for compatibility with the professional GIS Multi-Geometry architecture.
 */
export const ensureMultiType = (geom: any): any => {
    if (!geom || !geom.type) return geom;

    const typeMap: Record<string, string> = {
        'Point': 'MultiPoint',
        'LineString': 'MultiLineString',
        'Polygon': 'MultiPolygon'
    };

    if (typeMap[geom.type]) {
        return {
            type: typeMap[geom.type],
            coordinates: [geom.coordinates]
        };
    }

    return geom;
};
