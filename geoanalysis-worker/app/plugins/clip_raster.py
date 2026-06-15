import os
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class ClipRasterPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "clip_raster_by_mask"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        raster_path = local_inputs.get("source_raster")
        mask_path = local_inputs.get("mask_vector")

        if not raster_path or not mask_path:
            raise ValueError("Both 'source_raster' and 'mask_vector' are required for clip_raster_by_mask plugin")

        # Параметры
        nodata = params.get("nodata_value", 0)
        crop_to_cutline = bool(params.get("crop_to_cutline", True))
        
        # Генерируем имя выходного файла
        original_name = os.path.basename(raster_path)
        name_root, _ = os.path.splitext(original_name)
        output_filename = f"clipped_{name_root}.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(f"Clipping raster {raster_path} by mask {mask_path}")

        self._clip_raster(raster_path, mask_path, output_path, nodata, crop_to_cutline)

        return {"raster_result": output_path}

    def _clip_raster(self, src_file, mask_file, dst_file, nodata, crop_to_cutline):
        gdal.UseExceptions()
        
        # Настройка опций Warp
        # Используем формат COG для совместимости с системой
        warp_options = gdal.WarpOptions(
            format="COG",
            cutlineDSName=mask_file,
            cropToCutline=crop_to_cutline,
            dstNodata=nodata,
            creationOptions=[
                "COMPRESS=DEFLATE",
                "PREDICTOR=2",
                "OVERVIEWS=AUTO",
                "BLOCKSIZE=512"
            ],
            multithread=True
        )

        logger.info(f"Starting GDAL Warp for clipping...")
        try:
            gdal.Warp(dst_file, src_file, options=warp_options)
            logger.info(f"Raster clipped successfully: {dst_file}")
        except Exception as e:
            logger.error(f"GDAL Warp failed: {e}")
            raise RuntimeError(f"Failed to clip raster: {e}")
