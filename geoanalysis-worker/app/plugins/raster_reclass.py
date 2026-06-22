import os
import json
import numpy as np
import rasterio
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class RasterReclassPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "raster_reclass"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        raster_path = local_inputs.get("source_raster")
        if not raster_path:
            raise ValueError("Input 'source_raster' is required for raster_reclass plugin")

        rules_raw = params.get("rules")
        if not rules_raw:
            raise ValueError("Parameter 'rules' is required for raster_reclass plugin")

        # Парсинг правил, если они пришли в виде строки JSON
        if isinstance(rules_raw, str):
            try:
                rules = json.loads(rules_raw)
            except Exception as e:
                raise ValueError(f"Could not parse rules JSON string: {e}")
        else:
            rules = rules_raw

        if not isinstance(rules, list):
            raise ValueError("Rules parameter must be a list of rules")

        default_value = params.get("default_value", 0)

        output_filename = "reclassified.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(f"Reclassifying raster {raster_path} with {len(rules)} rules")
        self._reclassify_raster(raster_path, output_path, rules, default_value)

        return {"raster_result": output_path}

    def _reclassify_raster(self, src_file: str, dst_file: str, rules: list, default_value: Any):
        gdal.UseExceptions()

        try:
            with rasterio.open(src_file) as src:
                arr = src.read(1)
                profile = src.profile.copy()

                # Создаем выходной массив, заполненный default_value
                out_arr = np.full(arr.shape, default_value, dtype=np.int32)

                for rule in rules:
                    if not isinstance(rule, list) or len(rule) != 3:
                        logger.warning(f"Skipping invalid rule format: {rule}")
                        continue
                    
                    min_val, max_val, new_val = rule
                    
                    # Применяем фильтр диапазона [min_val, max_val)
                    mask = (arr >= min_val) & (arr < max_val)
                    out_arr[mask] = int(new_val)

                # Настройка профиля выходного файла
                profile.update(
                    dtype=rasterio.int32,
                    count=1,
                    driver="GTiff"
                )

                # Запись во временный файл
                temp_path = dst_file + ".tmp.tif"
                if os.path.exists(temp_path):
                    os.remove(temp_path)

                with rasterio.open(temp_path, "w", **profile) as dst:
                    dst.write(out_arr, 1)

            # Трансляция в COG
            if os.path.exists(dst_file):
                os.remove(dst_file)

            logger.info("Translating reclassified raster to COG...")
            temp_ds = gdal.Open(temp_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary reclassified raster for COG translation")

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

            if os.path.exists(temp_path):
                os.remove(temp_path)

            logger.info("Reclassification successful")

        except Exception as e:
            logger.error(f"Reclassification failed: {e}")
            if 'temp_path' in locals() and os.path.exists(temp_path):
                try:
                    os.remove(temp_path)
                except Exception:
                    pass
            raise RuntimeError(f"Failed to reclassify raster: {e}")
