import os
import io
import requests
import rasterio
from rasterio.plot import show as rio_show
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

    # Set up matplotlib figure (A3 landscape dimensions in inches)
    fig, ax = plt.subplots(figsize=(16.54, 11.69), dpi=dpi)
    
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
                
            # Generate presigned URL if it points to S3
            if not url.startswith("http"):
                try:
                    # Strip s3:// prefix and any bucket name from the key
                    if url.startswith("s3://"):
                        url = url.replace("s3://", "")
                        parts = url.split("/", 1)
                        if len(parts) == 2 and parts[0] in ("geo-abstraction-input", "geo-print"):
                            url = parts[1]
                            
                    url = minio_client.presigned_get_object(MINIO_BUCKET_RASTER, url)
                except Exception as e:
                    logger.error("Failed to sign S3 url for %s: %s", url, str(e))
                    continue
                    
            try:
                with rasterio.open(url) as src:
                    # Plot raster layer
                    rio_show(src, ax=ax, alpha=opacity)
            except Exception as e:
                logger.error("Failed to render raster layer %s: %s", layer_name, str(e))
                
        elif layer_type == "WMS":
            url = layer.get("url")
            layer_name_param = layer.get("layerName")
            if not url or not layer_name_param:
                continue
                
            try:
                # Build WMS GetMap request
                wms_url = build_wms_getmap_url(url, layer_name_param, bbox, projection, dpi)
                logger.info("Fetching WMS image: %s", wms_url)
                response = requests.get(wms_url, timeout=30)
                if response.status_code == 200:
                    img = Image.open(io.BytesIO(response.content))
                    ax.imshow(img, extent=[bbox[0], bbox[2], bbox[1], bbox[3]], alpha=opacity, origin='upper')
                else:
                    logger.error("WMS request failed with status: %d", response.status_code)
            except Exception as e:
                logger.error("Failed to render WMS layer %s: %s", layer_name, str(e))

    # Add grid lines
    ax.grid(True, which='both', color='gray', linestyle='--', linewidth=0.5, alpha=0.7)
    
    # Save figure
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
        gpd.GeoSeries([geom]).plot(
            ax=ax,
            edgecolor=stroke,
            facecolor=fill if fill != "none" else "none",
            linewidth=stroke_width,
            alpha=fill_opacity * layer_opacity if fill != "none" else layer_opacity
        )

def build_wms_getmap_url(base_url: str, layer: str, bbox: list, crs: str, dpi: int) -> str:
    width = 1600
    height = 1200
    separator = "&" if "?" in base_url else "?"
    bbox_str = f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}"
    
    url = (
        f"{base_url}{separator}SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap"
        f"&LAYERS={layer}&STYLES=&SRS={crs}&BBOX={bbox_str}"
        f"&WIDTH={width}&HEIGHT={height}&FORMAT=image/png&TRANSPARENT=TRUE"
    )
    return url
