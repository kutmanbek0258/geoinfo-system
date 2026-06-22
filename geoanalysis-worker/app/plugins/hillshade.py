import os
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class HillshadePlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "hillshade"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dem_path = local_inputs.get("dem_file")
        if not dem_path:
            raise ValueError("Input 'dem_file' is required for hillshade plugin")

        # Параметры источника света
        azimuth = float(params.get("azimuth", 315.0))
        altitude = float(params.get("altitude", 45.0))

        output_filename = "hillshade.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(f"Calculating hillshade for {dem_path} (azimuth: {azimuth}, altitude: {altitude})")

        self._calculate_hillshade(dem_path, output_path, azimuth, altitude)

        return {"raster_result": output_path}

    def _calculate_hillshade(self, src_file, dst_file, azimuth, altitude):
        gdal.UseExceptions()

        src_ds = gdal.Open(src_file)
        if src_ds is None:
            raise RuntimeError(f"Could not open source file: {src_file}")

        # Опции для расчета теневой отмывки с конвертацией в COG
        dem_options = gdal.DEMProcessingOptions(
            format="COG",
            azimuth=azimuth,
            altitude=altitude,
            creationOptions=[
                "COMPRESS=DEFLATE",
                "PREDICTOR=2",
                "OVERVIEWS=AUTO",
                "BLOCKSIZE=512"
            ]
        )

        try:
            logger.info("Executing native GDAL DEMProcessing for hillshade...")
            gdal.DEMProcessing(dst_file, src_ds, "hillshade", options=dem_options)
            logger.info(f"Hillshade calculation successful, saved to {dst_file}")
        except Exception as e:
            logger.error(f"GDAL DEMProcessing hillshade failed: {e}")
            raise RuntimeError(f"Failed to calculate hillshade: {e}")
        finally:
            src_ds = None
