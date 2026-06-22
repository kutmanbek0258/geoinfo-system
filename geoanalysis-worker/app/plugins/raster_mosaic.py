import os
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class RasterMosaicPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "raster_mosaic"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        if not local_inputs:
            raise ValueError("At least one input raster tile is required for raster_mosaic plugin")

        # Получаем все пути к исходным растровым файлам
        src_files = list(local_inputs.values())

        resampling = params.get("resampling", "nearest").lower()
        # Маппинг алгоритмов передискретизации на строковые константы GDAL Warp
        resampling_mapping = {
            "nearest": "near",
            "bilinear": "bilinear",
            "cubic": "cubic",
            "cubicspline": "cubicspline",
            "lanczos": "lanczos",
            "average": "average",
            "mode": "mode"
        }
        resamp_alg = resampling_mapping.get(resampling, "near")

        nodata_val = params.get("nodata")
        if nodata_val is not None:
            try:
                nodata_val = float(nodata_val)
            except (ValueError, TypeError):
                nodata_val = None

        output_filename = "mosaic_result.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(
            f"Stitching {len(src_files)} rasters into mosaic (resampling: {resampling} -> {resamp_alg}, nodata: {nodata_val})"
        )

        self._mosaic(src_files, output_path, resamp_alg, nodata_val)

        return {"raster_result": output_path}

    def _mosaic(self, src_files: list, dst_file: str, resamp_alg: str, nodata_val: float):
        gdal.UseExceptions()

        # Открываем все исходные наборы данных
        src_ds_list = []
        for path in src_files:
            ds = gdal.Open(path)
            if ds is None:
                raise RuntimeError(f"Could not open source raster tile: {path}")
            src_ds_list.append(ds)

        temp_mosaic_path = dst_file + ".tmp.tif"

        try:
            # Настройка Warp параметров для объединения
            warp_kwargs = {
                "format": "GTiff",
                "resampleAlg": resamp_alg,
                "multithread": True
            }

            if nodata_val is not None:
                warp_kwargs["srcNodata"] = nodata_val
                warp_kwargs["dstNodata"] = nodata_val

            warp_options = gdal.WarpOptions(**warp_kwargs)

            if os.path.exists(temp_mosaic_path):
                os.remove(temp_mosaic_path)

            logger.info("Executing native GDAL Warp for mosaic stitching...")
            gdal.Warp(temp_mosaic_path, src_ds_list, options=warp_options)

            # Закрываем дескрипторы исходных файлов
            for i in range(len(src_ds_list)):
                src_ds_list[i] = None

            # Транслируем сшитый временный GeoTIFF в COG
            if os.path.exists(dst_file):
                os.remove(dst_file)

            logger.info("Translating mosaic result to COG...")
            temp_ds = gdal.Open(temp_mosaic_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary mosaic raster for COG translation")

            translate_options = gdal.TranslateOptions(
                format="COG",
                creationOptions=[
                    "COMPRESS=DEFLATE",
                    "PREDICTOR=2",
                    "OVERVIEWS=AUTO",
                    "BLOCKSIZE=512"
                ]
            )
            gdal.Translate(dst_file, temp_ds, options=translate_options)
            temp_ds = None

            if os.path.exists(temp_mosaic_path):
                os.remove(temp_mosaic_path)

            logger.info("Mosaic stitching completed successfully")

        except Exception as e:
            logger.error(f"Mosaic stitching failed: {e}")
            if os.path.exists(temp_mosaic_path):
                try:
                    os.remove(temp_mosaic_path)
                except Exception:
                    pass
            raise RuntimeError(f"Raster mosaic failed: {e}")
        finally:
            # Освобождаем память в любом случае
            for i in range(len(src_ds_list)):
                if src_ds_list[i] is not None:
                    src_ds_list[i] = None
