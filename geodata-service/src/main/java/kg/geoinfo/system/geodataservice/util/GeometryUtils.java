package kg.geoinfo.system.geodataservice.util;

import org.locationtech.jts.geom.*;

public class GeometryUtils {

    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public static Geometry ensure3D(Geometry geom) {
        if (geom == null) return null;

        Coordinate[] oldCoords = geom.getCoordinates();
        Coordinate[] newCoords = new Coordinate[oldCoords.length];

        for (int i = 0; i < oldCoords.length; i++) {
            Coordinate old = oldCoords[i];
            // Создаем Coordinate с 3-мя значениями (X, Y, Z), Z=0 если NaN
            newCoords[i] = new Coordinate(old.x, old.y, Double.isNaN(old.getZ()) ? 0.0 : old.getZ());
        }

        Geometry result;
        if (geom instanceof Point) {
            result = geometryFactory.createPoint(newCoords[0]);
        } else if (geom instanceof LineString) {
            result = geometryFactory.createLineString(newCoords);
        } else if (geom instanceof Polygon) {
            result = geometryFactory.createPolygon(newCoords);
        } else if (geom instanceof MultiLineString) {
            LineString[] lines = new LineString[geom.getNumGeometries()];
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                lines[i] = (LineString) ensure3D(geom.getGeometryN(i));
            }
            result = geometryFactory.createMultiLineString(lines);
        } else if (geom instanceof MultiPolygon) {
            Polygon[] polygons = new Polygon[geom.getNumGeometries()];
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                polygons[i] = (Polygon) ensure3D(geom.getGeometryN(i));
            }
            result = geometryFactory.createMultiPolygon(polygons);
        } else {
            result = geom;
        }

        if (result != null) {
            result.setSRID(4326);
        }
        return result;
    }

    public static MultiLineString ensureMultiLineString3D(Geometry geom) {
        Geometry geom3D = ensure3D(geom);
        if (geom3D instanceof MultiLineString) {
            return (MultiLineString) geom3D;
        } else if (geom3D instanceof LineString) {
            return geometryFactory.createMultiLineString(new LineString[]{(LineString) geom3D});
        } else {
            throw new IllegalArgumentException("Expected LineString or MultiLineString, but got " + (geom != null ? geom.getGeometryType() : "null"));
        }
    }

    public static MultiPolygon ensureMultiPolygon3D(Geometry geom) {
        Geometry geom3D = ensure3D(geom);
        if (geom3D instanceof MultiPolygon) {
            return (MultiPolygon) geom3D;
        } else if (geom3D instanceof Polygon) {
            return geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geom3D});
        }
        throw new IllegalArgumentException("Expected Polygon or MultiPolygon, but got " + (geom != null ? geom.getGeometryType() : "null"));
    }

    public static MultiPoint ensureMultiPoint3D(Geometry geom) {
        Geometry geom3D = ensure3D(geom);
        if (geom3D instanceof MultiPoint) {
            return (MultiPoint) geom3D;
        } else if (geom3D instanceof Point) {
            return geometryFactory.createMultiPoint(new Point[]{(Point) geom3D});
        }
        throw new IllegalArgumentException("Expected Point or MultiPoint, but got " + (geom != null ? geom.getGeometryType() : "null"));
    }
}
