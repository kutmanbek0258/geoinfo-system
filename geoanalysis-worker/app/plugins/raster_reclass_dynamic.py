import os
import json
import numpy as np
import rasterio
from osgeo import gdal
from typing import Dict, Any, Optional
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class RasterReclassDynamicPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "raster_reclass_dynamic"

    def get_schema(self) -> Optional[dict]:
        schema_path = os.path.join(os.path.dirname(__file__), "raster_reclass_dynamic.schema.json")
        with open(schema_path, "r", encoding="utf-8") as f:
            return json.load(f)

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        raster_path = local_inputs.get("source_raster")
        if not raster_path:
            raise ValueError("Input 'source_raster' is required for raster_reclass_dynamic plugin")

        rules_raw = params.get("rules")
        if not rules_raw:
            raise ValueError("Parameter 'rules' is required for raster_reclass_dynamic plugin")

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
                profile = src.profile.copy()
                data = src.read(1)

                reclassed = np.full(data.shape, default_value, dtype=np.float32)

                for rule in rules:
                    if len(rule) != 3:
                        logger.warning(f"Skipping invalid rule format: {rule}. Must be [min, max, new_value]")
                        continue
                    
                    min_val, max_val, new_val = float(rule[0]), float(rule[1]), float(rule[2])
                    mask = (data >= min_val) & (data < max_val)
                    reclassed[mask] = new_val

                profile.update(
                    dtype=rasterio.float32,
                    count=1,
                    nodata=default_value,
                    compress='deflate'
                )

                temp_reclass_path = dst_file + ".tmp.tif"
                with rasterio.open(temp_reclass_path, 'w', **profile) as dst:
                    dst.write(reclassed, 1)

            if os.path.exists(dst_file):
                try:
                    os.remove(dst_file)
                except Exception:
                    pass

            logger.info("Translating reclassified raster to COG format...")
            temp_ds = gdal.Open(temp_reclass_path)
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

            if os.path.exists(temp_reclass_path):
                os.remove(temp_reclass_path)

            logger.info(f"Reclassification successful, saved to {dst_file}")

        except Exception as e:
            logger.error(f"Raster reclass failed: {e}")
            if 'temp_reclass_path' in locals() and os.path.exists(temp_reclass_path):
                try:
                    os.remove(temp_reclass_path)
                except Exception:
                    pass
            raise RuntimeError(f"Failed to reclassify raster: {e}")
