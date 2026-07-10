import os
import io
import requests
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import geopandas as gpd
from shapely.geometry import shape
from PIL import Image
from ..core.config import logger, MINIO_BUCKET_RASTER
from ..core.clients import minio_client

def render_map(spec: dict, target_path: str):
    logger.info("Starting map canvas rendering")
    map_context = spec.get("mapContext", {})
    bbox = map_context.get("bbox")  # [minx, miny, maxx, maxy]
    projection = map_context.get("projection", "EPSG:4326")
    dpi = spec.get("dpi", 150)
    layers = spec.get("layers", [])

    if not bbox or len(bbox) != 4:
        raise ValueError("Invalid bbox in mapContext")

    # Page size maps for layout bounds calculation
    from reportlab.lib.pagesizes import A4, A3, A2, A1, A0, landscape, portrait
    size_map = {
        "A4": A4,
        "A3": A3,
        "A2": A2,
        "A1": A1,
        "A0": A0
    }
    
    page_format = "A3"
    is_landscape = True
    
    layout_name = spec.get("layout", "A3_LANDSCAPE")
    parts = layout_name.split("_")
    if len(parts) >= 1:
        if parts[0] in size_map:
            page_format = parts[0]
    if len(parts) >= 2:
        if parts[1] == "PORTRAIT":
            is_landscape = False
            
    page_size = size_map[page_format]
    if is_landscape:
        page_size = landscape(page_size)
        
    width, height = page_size
    
    margin = 14.17 # 0.5 cm
    doc_width = width - (2 * margin)
    doc_height = height - (2 * margin)
    map_height = doc_height * 0.80 # 80% of available height
    
    # Matplotlib figsize in inches (1 inch = 72 points)
    fig_w = doc_width / 72.0
    fig_h = map_height / 72.0
    
    logger.info("Setting up Matplotlib canvas with size: %f x %f inches", fig_w, fig_h)
    fig, ax = plt.subplots(figsize=(fig_w, fig_h), dpi=dpi)
    
    # Set extent/bounds
    ax.set_xlim(bbox[0], bbox[2])
    ax.set_ylim(bbox[1], bbox[3])
    
    # Enable axes ticks for the clean map look, but keep boundary or gridlines if needed
    ax.axis('on')
    
    # Process layers in reverse order (bottom to top)
    for layer in reversed(layers):
        layer_type = str(layer.get("type", "")).upper()
        opacity = float(layer.get("opacity", 1.0))
        layer_name = layer.get("layerName", "Layer")
        
        logger.info("Rendering layer: %s (Type: %s)", layer_name, layer_type)
        
        if layer_type == "VECTOR":
            features = layer.get("features")
            if not features:
                continue
                
            # If features is a list of features, wrap in a FeatureCollection
            if isinstance(features, list):
                features = {
                    "type": "FeatureCollection",
                    "features": features
                }
                
            try:
                gdf = gpd.GeoDataFrame.from_features(features)
                if gdf.empty:
                    continue
                gdf.set_crs("EPSG:4326", inplace=True)
                
                # Check if we need to reproject
                if projection != "EPSG:4326":
                    gdf = gdf.to_crs(projection)
                    
                # Plot with styles
                layer_style = layer.get("layerStyle") or {}
                plot_styled_gdf(gdf, ax, layer_style, opacity)
            except Exception as e:
                logger.error("Failed to render vector layer %s: %s", layer_name, str(e))
                
        elif layer_type == "RASTER" or layer_type == "COG":
            url = layer.get("url")
            if not url:
                continue
                
            try:
                titiler_endpoint = os.getenv("TITILER_ENDPOINT", "http://titiler:8000")
                s3_url = url if url.startswith("s3://") else f"s3://geo-abstraction-input/{url}"
                
                params = {
                    "url": s3_url,
                    "coord_crs": projection
                }
                
                colormap = layer.get("colormap")
                colormap_name = layer.get("colormapName") or layer.get("colormap_name")
                resampling = layer.get("resampling")
                
                if colormap_name:
                    params["colormap_name"] = colormap_name
                elif colormap:
                    params["colormap"] = colormap
                    
                if resampling:
                    params["resampling"] = resampling
                    
                bbox_str = f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}"
                titiler_url = f"{titiler_endpoint}/cog/bbox/{bbox_str}.png"
                
                logger.info("Fetching styled COG image from TiTiler: %s with params %s", titiler_url, params)
                response = requests.get(titiler_url, params=params, timeout=30)
                if response.status_code == 200:
                    img = Image.open(io.BytesIO(response.content))
                    ax.imshow(img, extent=[bbox[0], bbox[2], bbox[1], bbox[3]], alpha=opacity, origin='upper')
                else:
                    logger.error("Failed to fetch styled raster from TiTiler. Status code: %d, Response: %s", response.status_code, response.text)
            except Exception as e:
                logger.error("Failed to render styled raster layer %s: %s", layer_name, str(e))

    # Add grid lines
    ax.grid(True, which='both', color='gray', linestyle='--', linewidth=0.5, alpha=0.7)
    
    # Save figure
    fig.tight_layout()
    plt.savefig(target_path, bbox_inches='tight', pad_inches=0.1)
    plt.close(fig)
    logger.info("Map canvas rendered successfully to %s", target_path)
 
def plot_styled_gdf(gdf, ax, layer_style, layer_opacity):
    default_stroke = layer_style.get("strokeColor", layer_style.get("_strokeColor", "#000000"))
    default_fill = layer_style.get("fillColor", layer_style.get("_fillColor", "none"))
    default_stroke_width = float(layer_style.get("strokeWidth", layer_style.get("_strokeWidth", 1.5)))
    default_fill_opacity = float(layer_style.get("fillOpacity", layer_style.get("_fillOpacity", 0.5)))

    for idx, row in gdf.iterrows():
        geom = row.geometry
        if not geom:
            continue
            
        # Read from individual feature properties if present, otherwise default to layerStyle
        props = row.get("properties") or {} if hasattr(row, "get") else {}
        stroke = props.get("_strokeColor", props.get("strokeColor", default_stroke))
        fill = props.get("_fillColor", props.get("fillColor", default_fill))
        stroke_width = float(props.get("_strokeWidth", props.get("strokeWidth", default_stroke_width)))
        fill_opacity = float(props.get("_fillOpacity", props.get("fillOpacity", default_fill_opacity)))
        
        # Plot individual feature
        is_polygon = geom.geom_type in ("Polygon", "MultiPolygon")
        gpd.GeoSeries([geom]).plot(
            ax=ax,
            edgecolor=stroke,
            facecolor=fill if (fill != "none" and is_polygon) else "none",
            linewidth=stroke_width,
            alpha=fill_opacity * layer_opacity if (fill != "none" and is_polygon) else layer_opacity
        )
