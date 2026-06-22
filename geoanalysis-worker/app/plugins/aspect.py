import os
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class AspectPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "aspect"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dem_path = local_inputs.get("dem_file")
        if not dem_path:
            raise ValueError("Input 'dem_file' is required for aspect plugin")

        output_filename = "aspect.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(f"Calculating aspect for {dem_path}")

        self._calculate_aspect(dem_path, output_path)

        return {"raster_result": output_path}

    def _calculate_aspect(self, src_file, dst_file):
        gdal.UseExceptions()

        src_ds = gdal.Open(src_file)
        if src_ds is None:
            raise RuntimeError(f"Could not open source file: {src_file}")

        # Опции для расчета экспозиции с конвертацией в COG
        dem_options = gdal.DEMProcessingOptions(
            format="COG",
            creationOptions=[
                "COMPRESS=DEFLATE",
                "PREDICTOR=2",
                "OVERVIEWS=AUTO",
                "BLOCKSIZE=512"
            ]
        )

        try:
            logger.info("Executing native GDAL DEMProcessing for aspect...")
            gdal.DEMProcessing(dst_file, src_ds, "aspect", options=dem_options)
            logger.info(f"Aspect calculation successful, saved to {dst_file}")
        except Exception as e:
            logger.error(f"GDAL DEMProcessing aspect failed: {e}")
            raise RuntimeError(f"Failed to calculate aspect: {e}")
        finally:
            src_ds = None
