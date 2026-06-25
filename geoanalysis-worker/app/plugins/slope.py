import os
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class SlopePlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "slope"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dem_path = local_inputs.get("dem_file")
        if not dem_path:
            raise ValueError("Input 'dem_file' is required for slope plugin")

        # Параметры: degrees или percent
        units = params.get("units", "degrees")
        if units not in ["degrees", "percent"]:
            units = "degrees"

        output_filename = "slope.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(f"Calculating slope for {dem_path} (units: {units})")

        self._calculate_slope(dem_path, output_path, units)

        return {"raster_result": output_path}

    def _calculate_slope(self, src_file, dst_file, units):
        gdal.UseExceptions()

        src_ds = gdal.Open(src_file)
        if src_ds is None:
            raise RuntimeError(f"Could not open source file: {src_file}")

        # Detect geographic coordinate system (degrees) and apply scale factor for elevation in meters
        scale = 1.0
        srs = src_ds.GetSpatialRef()
        if srs and srs.IsGeographic():
            scale = 111120
            logger.info("Input DEM is in geographic coordinate system. Applying scale=111120 for slope calculation.")

        # Опции для расчета уклона с конвертацией в COG
        dem_options = gdal.DEMProcessingOptions(
            format="COG",
            slopeFormat=units,
            scale=scale,
            creationOptions=[
                "COMPRESS=DEFLATE",
                "PREDICTOR=2",
                "OVERVIEWS=AUTO",
                "BLOCKSIZE=512"
            ]
        )

        try:
            logger.info("Executing native GDAL DEMProcessing for slope...")
            gdal.DEMProcessing(dst_file, src_ds, "slope", options=dem_options)
            logger.info(f"Slope calculation successful, saved to {dst_file}")
        except Exception as e:
            logger.error(f"GDAL DEMProcessing slope failed: {e}")
            raise RuntimeError(f"Failed to calculate slope: {e}")
        finally:
            src_ds = None
