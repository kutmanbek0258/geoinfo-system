package kg.geoinfo.system.geoprintservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geoprintservice.dto.PrintSpecificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.Query;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.ows.wms.WMSUtils;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wms.map.WMSLayer;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.referencing.CRS;
import org.geotools.data.DataUtilities;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapRenderer {

    private final ObjectMapper objectMapper;

    public BufferedImage renderMap(PrintSpecificationDto spec) throws Exception {
        String layoutName = spec.getLayout() != null ? spec.getLayout().toUpperCase() : "A4_LANDSCAPE";
        
        int dpi = getDpiForLayout(layoutName);
        double[] sizeMm = getSizeMmForLayout(layoutName);
        boolean isPortrait = layoutName.contains("PORTRAIT");
        
        double widthMm = (isPortrait ? sizeMm[1] : sizeMm[0]) - 10;
        double heightMm = (isPortrait ? sizeMm[0] : sizeMm[1]) - 10;

        int widthPx = (int) (widthMm / 25.4 * dpi);
        int heightPx = (int) (heightMm / 25.4 * dpi);

        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        MapContent mapContent = new MapContent();
        CoordinateReferenceSystem mapCrs = CRS.decode(spec.getMapContext().getProjection(), true);
        mapContent.getViewport().setCoordinateReferenceSystem(mapCrs);

        for (PrintSpecificationDto.LayerSpecDto layerSpec : spec.getLayers()) {
            try {
                if ("WMS".equals(layerSpec.getType())) {
                    mapContent.addLayer(createWmsLayer(layerSpec));
                } else if ("VECTOR".equals(layerSpec.getType())) {
                    mapContent.addLayer(createVectorLayer(layerSpec));
                }
            } catch (Exception e) {
                log.error("Layer error: {}", e.getMessage());
            }
        }

        GTRenderer renderer = new StreamingRenderer();
        renderer.setMapContent(mapContent);

        double[] bbox = spec.getMapContext().getBbox();
        ReferencedEnvelope requestedEnv = new ReferencedEnvelope(bbox[0], bbox[2], bbox[1], bbox[3], mapCrs);
        
        double imgAspect = (double) widthPx / heightPx;
        double dataAspect = requestedEnv.getWidth() / requestedEnv.getHeight();
        
        ReferencedEnvelope finalEnv = new ReferencedEnvelope(requestedEnv);
        if (imgAspect > dataAspect) {
            double newWidth = requestedEnv.getHeight() * imgAspect;
            double delta = (newWidth - requestedEnv.getWidth()) / 2;
            finalEnv = new ReferencedEnvelope(requestedEnv.getMinX() - delta, requestedEnv.getMaxX() + delta, 
                                            requestedEnv.getMinY(), requestedEnv.getMaxY(), mapCrs);
        } else {
            double newHeight = requestedEnv.getWidth() / imgAspect;
            double delta = (newHeight - requestedEnv.getHeight()) / 2;
            finalEnv = new ReferencedEnvelope(requestedEnv.getMinX(), requestedEnv.getMaxX(), 
                                            requestedEnv.getMinY() - delta, requestedEnv.getMaxY() + delta, mapCrs);
        }

        renderer.paint(g2d, new Rectangle(0, 0, widthPx, heightPx), finalEnv);

        g2d.dispose();
        mapContent.dispose();
        return image;
    }

    private int getDpiForLayout(String layout) {
        if (layout.startsWith("A0") || layout.startsWith("A1")) return 250;
        return 300;
    }

    private double[] getSizeMmForLayout(String layout) {
        if (layout.startsWith("A0")) return new double[]{1189, 841};
        if (layout.startsWith("A1")) return new double[]{841, 594};
        if (layout.startsWith("A2")) return new double[]{594, 420};
        if (layout.startsWith("A3")) return new double[]{420, 297};
        return new double[]{297, 210};
    }

    private Layer createWmsLayer(PrintSpecificationDto.LayerSpecDto spec) throws Exception {
        URL url = new URL(spec.getUrl() + "?service=WMS&version=1.1.1&request=GetCapabilities");
        WebMapServer wms = new WebMapServer(url);
        org.geotools.ows.wms.Layer wmsLayer = null;
        for (org.geotools.ows.wms.Layer layer : WMSUtils.getNamedLayers(wms.getCapabilities())) {
            if (layer.getName().equals(spec.getLayerName())) {
                wmsLayer = layer; break;
            }
        }
        if (wmsLayer == null) wmsLayer = WMSUtils.getNamedLayers(wms.getCapabilities())[0];
        
        return new WMSLayer(wms, wmsLayer);
    }

    private Layer createVectorLayer(PrintSpecificationDto.LayerSpecDto spec) throws Exception {
        String geojson = objectMapper.writeValueAsString(spec.getFeatures());
        FeatureJSON fjson = new FeatureJSON();
        SimpleFeatureCollection features = (SimpleFeatureCollection) fjson.readFeatureCollection(geojson);

        if (features == null || features.isEmpty()) {
            log.warn("Vector layer is empty or malformed, skipping.");
            throw new IllegalArgumentException("Vector layer has no features");
        }

        CoordinateReferenceSystem vectorCrs = CRS.decode("EPSG:4326", true);
        SimpleFeatureSource source = DataUtilities.source(features);
        
        if (features.getSchema().getGeometryDescriptor() == null) {
            log.error("No geometry descriptor found in vector layer schema. Features may be missing geometry data.");
            throw new IllegalArgumentException("No geometry found in vector data");
        }
        String geometryPropertyName = features.getSchema().getGeometryDescriptor().getLocalName();

        Query query = new Query();
        query.setCoordinateSystem(vectorCrs);
        SimpleFeatureSource view = DataUtilities.createView(source, query);

        // Читаем цвет из layer-level style (fallback на синий)
        Map<String, Object> styleSpec = spec.getLayerStyle() != null ? spec.getLayerStyle() : java.util.Collections.emptyMap();
        String strokeColorStr = (String) styleSpec.getOrDefault("strokeColor", "#3399CC");
        double strokeWidth = Double.parseDouble(styleSpec.getOrDefault("strokeWidth", 2).toString());
        String fillColorStr = (String) styleSpec.getOrDefault("fillColor", "#3399CC");
        double fillOpacity = Double.parseDouble(styleSpec.getOrDefault("fillOpacity", "0.4").toString());

        org.geotools.styling.StyleFactory sf = org.geotools.factory.CommonFactoryFinder.getStyleFactory();
        org.opengis.filter.FilterFactory2 ff = org.geotools.factory.CommonFactoryFinder.getFilterFactory2();

        org.geotools.styling.Stroke stroke = sf.createStroke(
            ff.literal(strokeColorStr),
            ff.literal(strokeWidth)
        );

        java.awt.Color fillAwtColor = java.awt.Color.decode(fillColorStr);
        org.geotools.styling.Fill fill = sf.createFill(
            ff.literal(fillColorStr),
            ff.literal(fillOpacity)
        );

        // Symbolizers
        org.geotools.styling.Graphic graphic = sf.createDefaultGraphic();
        graphic.graphicalSymbols().clear();
        org.geotools.styling.Mark mark = sf.createMark();
        mark.setWellKnownName(ff.literal("circle"));
        mark.setFill(fill);
        mark.setStroke(stroke);
        graphic.graphicalSymbols().add(mark);
        graphic.setSize(ff.literal(10));

        org.geotools.styling.PointSymbolizer pointSym = sf.createPointSymbolizer(graphic, geometryPropertyName);
        org.geotools.styling.LineSymbolizer lineSym = sf.createLineSymbolizer(stroke, geometryPropertyName);
        org.geotools.styling.PolygonSymbolizer polySym = sf.createPolygonSymbolizer(stroke, fill, geometryPropertyName);

        // Фильтры по типу геометрии через OGC-функцию geometryType()
        // Используем ff.property("") для обращения к геометрии по умолчанию
        org.opengis.filter.Filter pointFilter = ff.or(java.util.List.of(
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("Point")),
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("MultiPoint")),
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("point")),
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("multipoint"))
        ));
        org.opengis.filter.Filter lineFilter = ff.or(java.util.List.of(
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("LineString")),
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("MultiLineString")),
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("linestring")),
            ff.equals(ff.function("geometryType", ff.property("")), ff.literal("multilinestring"))
        ));

        // Для полигонов используем исключение: все что не точка и не линия
        org.opengis.filter.Filter polyFilter = ff.not(ff.or(pointFilter, lineFilter));

        org.geotools.styling.Rule pointRule = sf.createRule();
        pointRule.setFilter(pointFilter);
        pointRule.symbolizers().add(pointSym);

        org.geotools.styling.Rule lineRule = sf.createRule();
        lineRule.setFilter(lineFilter);
        lineRule.symbolizers().add(lineSym);

        org.geotools.styling.Rule polyRule = sf.createRule();
        polyRule.setFilter(polyFilter);
        polyRule.symbolizers().add(polySym);

        org.geotools.styling.FeatureTypeStyle fts = sf.createFeatureTypeStyle(
            new org.geotools.styling.Rule[]{pointRule, lineRule, polyRule}
        );
        Style style = sf.createStyle();
        style.featureTypeStyles().add(fts);

        return new FeatureLayer(view, style);
    }
}
