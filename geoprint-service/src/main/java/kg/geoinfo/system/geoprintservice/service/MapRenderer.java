package kg.geoinfo.system.geoprintservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geoprintservice.dto.PrintSpecificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.data.simple.SimpleFeatureCollection;
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

        int dpi = spec.getDpi() != null ? spec.getDpi() : 300;
        log.info("Starting map rendering with DPI: {}, layout: {}", dpi, spec.getLayout());
        
        // Расчет размеров на основе layout (A4 Landscape = 297x210 mm)
        int width = (int) (11.69 * dpi);
        int height = (int) (8.27 * dpi);

        if ("A4_PORTRAIT".equals(spec.getLayout())) {
            int tmp = width;
            width = height;
            height = tmp;
        }
        log.info("Target image dimensions: {}x{} pixels", width, height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        MapContent mapContent = new MapContent();

        log.info("Adding {} layers to map content", spec.getLayers().size());
        for (PrintSpecificationDto.LayerSpecDto layerSpec : spec.getLayers()) {
            if ("WMS".equals(layerSpec.getType())) {
                log.debug("Adding WMS layer: {} from {}", layerSpec.getLayerName(), layerSpec.getUrl());
                mapContent.addLayer(createWmsLayer(layerSpec));
            } else if ("VECTOR".equals(layerSpec.getType())) {
                log.debug("Adding VECTOR layer");
                mapContent.addLayer(createVectorLayer(layerSpec));
            }
        }

        GTRenderer renderer = new StreamingRenderer();
        renderer.setMapContent(mapContent);

        double[] bbox = spec.getMapContext().getBbox();
        log.info("Map BBox: [{}, {}, {}, {}], Projection: {}", bbox[0], bbox[1], bbox[2], bbox[3], spec.getMapContext().getProjection());
        
        ReferencedEnvelope mapArea = new ReferencedEnvelope(
                bbox[0], bbox[2], bbox[1], bbox[3],
                CRS.decode(spec.getMapContext().getProjection())
        );

        Rectangle paintArea = new Rectangle(0, 0, width, height);
        
        long startTime = System.currentTimeMillis();
        log.info("Starting GeoTools paint operation...");
        renderer.paint(g2d, paintArea, mapArea);
        log.info("GeoTools paint operation completed in {} ms", (System.currentTimeMillis() - startTime));

        g2d.dispose();
        mapContent.dispose();

        return image;
    }

    private Layer createWmsLayer(PrintSpecificationDto.LayerSpecDto spec) throws Exception {
        URL url = new URL(spec.getUrl() + "?service=WMS&version=1.1.1&request=GetCapabilities");
        WebMapServer wms = new WebMapServer(url);
        org.geotools.ows.wms.Layer wmsLayer = WMSUtils.getNamedLayers(wms.getCapabilities())[0]; // Упрощение: берем первый подходящий
        
        // В реальном проекте нужно найти слой по имени: spec.getLayerName()
        for (org.geotools.ows.wms.Layer layer : WMSUtils.getNamedLayers(wms.getCapabilities())) {
            if (layer.getName().equals(spec.getLayerName())) {
                wmsLayer = layer;
                break;
            }
        }

        return new WMSLayer(wms, wmsLayer);
    }

    private Layer createVectorLayer(PrintSpecificationDto.LayerSpecDto spec) throws Exception {
        String geojson = objectMapper.writeValueAsString(spec.getFeatures());
        FeatureJSON fjson = new FeatureJSON();
        SimpleFeatureCollection features = (SimpleFeatureCollection) fjson.readFeatureCollection(geojson);
        
        // Базовая стилизация
        Style style = SLD.createSimpleStyle(features.getSchema());
        Map<String, Object> styleSpec = spec.getStyle();
        if (styleSpec != null) {
            String color = (String) styleSpec.getOrDefault("strokeColor", "#FF0000");
            Double width = Double.valueOf(styleSpec.getOrDefault("strokeWidth", 2.0).toString());
            style = SLD.createLineStyle(Color.decode(color), width.floatValue());
        }

        return new FeatureLayer(features, style);
    }
}
