package kg.geoinfo.system.geodataservice.util;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryTransformer;

public class GeometryUtils {

    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public static Geometry ensure3D(Geometry geom) {
        if (geom == null) return null;

        GeometryTransformer transformer = new GeometryTransformer() {
            protected CoordinateSequence transformCoordinateSequence(CoordinateSequence coords, Geometry parent) {
                CoordinateSequenceFactory factory = geometryFactory.getCoordinateSequenceFactory();
                CoordinateSequence newSeq = factory.create(coords.size(), 3);
                for (int i = 0; i < coords.size(); i++) {
                    newSeq.setOrdinate(i, 0, coords.getX(i));
                    newSeq.setOrdinate(i, 1, coords.getY(i));
                    double z = coords.getZ(i);
                    newSeq.setOrdinate(i, 2, Double.isNaN(z) ? 0.0 : z);
                }
                return newSeq;
            }
        };

        return transformer.transform(geom);
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

    public static Polygon ensurePolygon3D(Geometry geom) {
        Geometry geom3D = ensure3D(geom);
        if (geom3D instanceof Polygon) {
            return (Polygon) geom3D;
        } else if (geom3D instanceof MultiPolygon) {
            if (geom3D.getNumGeometries() > 0) {
                return (Polygon) geom3D.getGeometryN(0);
            }
        }
        throw new IllegalArgumentException("Expected Polygon or MultiPolygon, but got " + (geom != null ? geom.getGeometryType() : "null"));
    }

    public static Point ensurePoint3D(Geometry geom) {
        Geometry geom3D = ensure3D(geom);
        if (geom3D instanceof Point) {
            return (Point) geom3D;
        }
        throw new IllegalArgumentException("Expected Point, but got " + (geom != null ? geom.getGeometryType() : "null"));
    }
}
