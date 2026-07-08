package kg.geoinfo.system.geoprintservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintSpecificationDto {
    private java.util.UUID projectId;
    private String layout;
    private Integer dpi;
    private MapContextDto mapContext;
    private List<LayerSpecDto> layers;
    private Map<String, Object> attributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapContextDto {
        private String projection;
        private double[] bbox;
        private Double rotation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayerSpecDto {
        private String type; // COG, VECTOR
        private java.util.UUID layerId;
        private String url;
        private String layerName;
        private Double opacity;
        private Object features; // GeoJSON
        private Map<String, Object> layerStyle;
        private String colormap;
        private String colormapName;
        private String resampling;
        private java.util.List<java.util.UUID> pointIds;
        private java.util.List<java.util.UUID> multilineIds;
        private java.util.List<java.util.UUID> polygonIds;
    }
}
