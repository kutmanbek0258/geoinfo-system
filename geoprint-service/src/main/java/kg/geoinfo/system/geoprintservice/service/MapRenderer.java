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
        
        double widthMm = isPortrait ? sizeMm[1] : sizeMm[0];
        double heightMm = isPortrait ? sizeMm[0] : sizeMm[1];

        int widthPx = (int) (widthMm / 25.4 * dpi);
        int heightPx = (int) (heightMm / 25.4 * dpi);

        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        MapContent mapContent = new MapContent();
        CoordinateReferenceSystem mapCrs = CRS.decode(spec.getMapContext().getProjection());
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
        
        CoordinateReferenceSystem vectorCrs = CRS.decode("EPSG:4326");
        SimpleFeatureSource source = DataUtilities.source(features);
        
        Query query = new Query();
        query.setCoordinateSystem(vectorCrs);
        
        SimpleFeatureSource view = DataUtilities.createView(source, query);
        
        Map<String, Object> styleSpec = spec.getStyle();
        String colorStr = (styleSpec != null) ? (String) styleSpec.getOrDefault("strokeColor", "#FF0000") : "#FF0000";
        Color color = Color.decode(colorStr);
        float width = (styleSpec != null) ? Float.parseFloat(styleSpec.getOrDefault("strokeWidth", 2.0).toString()) : 2.0f;

        Style style = SLD.createSimpleStyle(view.getSchema(), color);
        
        return new FeatureLayer(view, style);
    }
}
