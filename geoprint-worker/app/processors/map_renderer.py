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
    
    # Add cartographic elements (North Arrow & Scale Bar)
    add_north_arrow(ax)
    add_scale_bar(ax, bbox, projection)
    
    # Save figure
    fig.tight_layout()
    plt.savefig(target_path, bbox_inches='tight', pad_inches=0.1)
    plt.close(fig)
    logger.info("Map canvas rendered successfully to %s", target_path)

def add_north_arrow(ax, loc=(0.95, 0.93), size=0.03):
    import matplotlib.patches as patches
    x, y = loc
    # Draw vector compass needle pointing North
    left_needle = patches.Polygon(
        [[x, y + size], [x - size * 0.35, y], [x, y - size * 0.1]],
        facecolor='#0f172a', edgecolor='#0f172a', transform=ax.transAxes, zorder=10
    )
    right_needle = patches.Polygon(
        [[x, y + size], [x + size * 0.35, y], [x, y - size * 0.1]],
        facecolor='#cbd5e1', edgecolor='#0f172a', transform=ax.transAxes, zorder=10
    )
    ax.add_patch(left_needle)
    ax.add_patch(right_needle)
    
    # Add N label
    ax.text(
        x, y + size * 1.2, 'N',
        color='#0f172a', weight='bold', size=11,
        ha='center', va='center', transform=ax.transAxes, zorder=10
    )

def add_scale_bar(ax, bbox, projection, loc=(0.05, 0.05)):
    import math
    from pyproj import Transformer
    
    try:
        # Convert bbox corners to WGS84 to calculate geodesic width
        transformer = Transformer.from_crs(projection, "EPSG:4326", always_xy=True)
        lon_min, lat_min = transformer.transform(bbox[0], bbox[1])
        lon_max, lat_max = transformer.transform(bbox[2], bbox[1])
        
        lat_center = (lat_min + lat_max) / 2.0
        width_meters = abs(lon_max - lon_min) * 111320.0 * math.cos(math.radians(lat_center))
        
        if width_meters <= 0:
            return
            
        # Target scale bar: ~20% of map width
        target_len = width_meters * 0.20
        
        # Round target length to nice cartographic steps (1, 2, 5)
        exponent = int(math.log10(target_len)) if target_len > 0 else 0
        base = 10 ** exponent
        nice_length = base
        for step in (1, 2, 5):
            if target_len <= step * base:
                nice_length = step * base
                break
        else:
            nice_length = base * 10
            
        # Convert nice length in meters back to map coordinate width
        nice_len_coords = nice_length * (bbox[2] - bbox[0]) / width_meters
        
        # Base coordinates for scale bar
        x_start = bbox[0] + loc[0] * (bbox[2] - bbox[0])
        y_start = bbox[1] + loc[1] * (bbox[3] - bbox[1])
        x_end = x_start + nice_len_coords
        
        # Tick height (approx. 1.2% of map height)
        tick_h = 0.012 * (bbox[3] - bbox[1])
        
        # Format label
        if nice_length >= 1000:
            label = f"{int(nice_length / 1000)} km"
        else:
            label = f"{int(nice_length)} m"
            
        # Draw the main line
        ax.plot([x_start, x_end], [y_start, y_start], color='#0f172a', linewidth=2.5, zorder=10)
        # Draw ticks at ends and center
        ax.plot([x_start, x_start], [y_start - tick_h, y_start + tick_h], color='#0f172a', linewidth=2.5, zorder=10)
        ax.plot([x_end, x_end], [y_start - tick_h, y_start + tick_h], color='#0f172a', linewidth=2.5, zorder=10)
        
        x_mid = (x_start + x_end) / 2.0
        ax.plot([x_mid, x_mid], [y_start - tick_h * 0.7, y_start + tick_h * 0.7], color='#0f172a', linewidth=1.5, zorder=10)
        
        # Label centered above
        ax.text(
            x_mid, y_start + tick_h * 1.4, label,
            color='#0f172a', size=9, weight='bold',
            ha='center', va='bottom', zorder=10,
            bbox=dict(boxstyle="square,pad=0.2", facecolor="white", edgecolor="none", alpha=0.7)
        )
        logger.info("Scale bar added: %s (width: %f coords)", label, nice_len_coords)
    except Exception as e:
        logger.error("Failed to add scale bar to map canvas: %s", str(e))
 
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
        
        # Check characteristics for individual style overrides
        characteristics = row.get("characteristics")
        if characteristics is None and hasattr(row, "get") and "properties" in row:
            characteristics = props.get("characteristics")
            
        if isinstance(characteristics, str):
            try:
                import json
                characteristics = json.loads(characteristics)
            except Exception:
                pass
                
        if isinstance(characteristics, dict):
            logger.info("Found feature characteristics: %s", characteristics)
            style = characteristics.get("style")
            if isinstance(style, dict):
                logger.info("Found feature style override: %s", style)
                # 1. Line / Stroke style
                line_style = style.get("line") or {}
                stroke = line_style.get("color", stroke)
                if "width" in line_style:
                    stroke_width = float(line_style["width"])
                    
                # 2. Polygon / Fill style
                poly_style = style.get("poly") or {}
                raw_fill = poly_style.get("fillColor")
                if raw_fill:
                    if raw_fill.startswith("rgba"):
                         try:
                             parts = raw_fill.replace("rgba(", "").replace(")", "").split(",")
                             if len(parts) == 4:
                                 r = int(parts[0].strip())
                                 g = int(parts[1].strip())
                                 b = int(parts[2].strip())
                                 fill = f"#{r:02x}{g:02x}{b:02x}"
                                 fill_opacity = float(parts[3].strip())
                         except Exception:
                             fill = raw_fill
                    else:
                        fill = raw_fill
                        fill_opacity = 1.0
            else:
                logger.info("No style override found in characteristics for feature %s", row.get("id", "unknown"))
        else:
            logger.info("No characteristics found for feature %s", row.get("id", "unknown"))

        # Plot individual feature with proper geometry types and separate transparencies
        import matplotlib.colors as mcolors
        
        is_polygon = geom.geom_type in ("Polygon", "MultiPolygon")
        is_point = geom.geom_type in ("Point", "MultiPoint")
        
        edge_rgba = mcolors.to_rgba(stroke, alpha=1.0)
        
        if is_polygon:
            face_rgba = mcolors.to_rgba(fill, alpha=fill_opacity) if fill != "none" else "none"
        elif is_point:
            face_rgba = mcolors.to_rgba(stroke, alpha=1.0)
        else:
            face_rgba = "none"
            
        gpd.GeoSeries([geom]).plot(
            ax=ax,
            edgecolor=edge_rgba,
            facecolor=face_rgba,
            linewidth=stroke_width,
            alpha=layer_opacity
        )
