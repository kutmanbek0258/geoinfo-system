import os
import geopandas as gpd
from osgeo import gdal, ogr, osr
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class TerrainContoursPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "terrain_contours"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dem_path = local_inputs.get("dem_file")
        if not dem_path:
            raise ValueError("Input 'dem_file' is required for terrain_contours plugin")

        interval = float(params.get("interval", 10.0))
        base = float(params.get("base", 0.0))
        elev_field = params.get("attribute", "elev")
        use_3d = bool(params.get("use_3d", False))
        output_format = params.get("format", "GeoJSON")

        output_filename = f"contours_{int(interval)}m"
        extension = "geojson" if output_format.lower() == "geojson" else "shp"
        output_path = os.path.join(workspace, f"{output_filename}.{extension}")

        logger.info(f"Generating contours for {dem_path} with interval {interval}, 3D={use_3d}")

        self._generate_contours(dem_path, output_path, interval, base, elev_field, use_3d, output_format)

        return {"vector_result": output_path}

    def _generate_contours(self, src_file, dst_file, interval, base, elev_field, use_3d, output_format):
        gdal.UseExceptions()
        
        src_ds = gdal.Open(src_file)
        if src_ds is None:
            raise RuntimeError(f"Could not open {src_file}")
            
        src_band = src_ds.GetRasterBand(1)
        
        # Создаем временный слой в памяти для промежуточного хранения изолиний
        mem_drv = ogr.GetDriverByName("Memory")
        mem_ds = mem_drv.CreateDataSource("mem_ds")
        
        srs = osr.SpatialReference()
        srs.ImportFromWkt(src_ds.GetProjectionRef())

        geom_type = ogr.wkbLineString25D if use_3d else ogr.wkbLineString
        mem_layer = mem_ds.CreateLayer("temp_contours", srs=srs, geom_type=geom_type)
        
        field_defn = ogr.FieldDefn(elev_field, ogr.OFTReal)
        mem_layer.CreateField(field_defn)
        elev_field_idx = mem_layer.GetLayerDefn().GetFieldIndex(elev_field)

        # Генерируем изолинии во временный слой в памяти
        gdal.ContourGenerate(src_band, interval, base, [], 0, 0, mem_layer, -1, elev_field_idx)
        
        # Используем GeoPandas для быстрого экспорта через pyogrio
        # Это значительно быстрее ручного перебора фич OGR
        logger.info(f"Contouring finished. Converting memory layer to GeoDataFrame and saving via pyogrio.")
        
        # Читаем из памяти в GeoDataFrame
        gdf = gpd.read_file(mem_ds, layer="temp_contours")
        
        if not gdf.empty:
            # Сохраняем результат, используя движок pyogrio
            engine = "pyogrio"
            driver = "GeoJSON" if output_format.lower() == "geojson" else "ESRI Shapefile"
            
            gdf.to_file(dst_file, driver=driver, engine=engine)
            logger.info(f"Contours saved successfully to {dst_file} using {engine}")
        else:
            logger.warning("No contours were generated (empty output). Creating empty file.")
            gdf.to_file(dst_file, driver="GeoJSON")

        mem_ds = None # Cleanup memory datasource
        src_ds = None

