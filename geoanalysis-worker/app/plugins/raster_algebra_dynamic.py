import os
import numpy as np
import rasterio
import json
from osgeo import gdal
from typing import Dict, Any, Optional
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class RasterAlgebraDynamicPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "raster_algebra_dynamic"

    def get_schema(self) -> Optional[dict]:
        schema_path = os.path.join(os.path.dirname(__file__), "raster_algebra_dynamic.schema.json")
        with open(schema_path, "r", encoding="utf-8") as f:
            return json.load(f)

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        expression = params.get("expression")
        if not expression:
            raise ValueError("Parameter 'expression' is required for raster_algebra_dynamic plugin")

        if not local_inputs:
            raise ValueError("At least one input raster is required for raster_algebra_dynamic plugin")

        output_filename = "algebra_result.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(f"Evaluating map algebra expression: '{expression}' with variables: {list(local_inputs.keys())}")
        self._evaluate_algebra(local_inputs, output_path, expression)

        return {"raster_result": output_path}

    def _evaluate_algebra(self, local_inputs: Dict[str, str], dst_file: str, expression: str):
        gdal.UseExceptions()

        try:
            variables = {}
            first_profile = None
            first_shape = None

            # Чтение всех входящих растров
            for var_name, raster_path in local_inputs.items():
                if not var_name.isidentifier():
                    raise ValueError(f"Input key '{var_name}' is not a valid Python identifier")

                with rasterio.open(raster_path) as src:
                    arr = src.read(1).astype(np.float32)
                    variables[var_name] = arr

                    if first_profile is None:
                        first_profile = src.profile.copy()
                        first_shape = arr.shape
                    else:
                        if arr.shape != first_shape:
                            raise ValueError(
                                f"Input raster {var_name} has shape {arr.shape}, "
                                f"which does not match the first raster shape {first_shape}"
                            )

            # Безопасный контекст выполнения формулы
            allowed_names = {
                'np': np,
                'sin': np.sin,
                'cos': np.cos,
                'tan': np.tan,
                'arcsin': np.arcsin,
                'arccos': np.arccos,
                'arctan': np.arctan,
                'sinh': np.sinh,
                'cosh': np.cosh,
                'tanh': np.tanh,
                'exp': np.exp,
                'log': np.log,
                'log10': np.log10,
                'sqrt': np.sqrt,
                'abs': np.abs,
                'pi': np.pi,
                'e': np.e,
                **variables
            }

            # Вычисление формулы
            result = eval(expression, {"__builtins__": None}, allowed_names)

            # Сохранение временного растра
            first_profile.update(
                dtype=rasterio.float32,
                count=1,
                compress='deflate'
            )

            temp_algebra_path = dst_file + ".tmp.tif"
            with rasterio.open(temp_algebra_path, 'w', **first_profile) as dst:
                dst.write(result.astype(np.float32), 1)

            if os.path.exists(dst_file):
                try:
                    os.remove(dst_file)
                except Exception:
                    pass

            logger.info("Translating algebra result to COG format...")
            temp_ds = gdal.Open(temp_algebra_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary algebra result for COG translation")

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

            if os.path.exists(temp_algebra_path):
                os.remove(temp_algebra_path)

            logger.info(f"Map algebra evaluation successful, saved to {dst_file}")

        except Exception as e:
            logger.error(f"Map algebra evaluation failed: {e}")
            if 'temp_algebra_path' in locals() and os.path.exists(temp_algebra_path):
                try:
                    os.remove(temp_algebra_path)
                except Exception:
                    pass
            raise RuntimeError(f"Failed to evaluate expression: {e}")
