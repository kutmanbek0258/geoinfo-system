import os
import numpy as np
import rasterio
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class RasterAlgebraPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "raster_algebra"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        expression = params.get("expression")
        if not expression:
            raise ValueError("Parameter 'expression' is required for raster_algebra plugin")

        if not local_inputs:
            raise ValueError("At least one input raster is required for raster_algebra plugin")

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
                # Проверяем, что ключ является валидным именем переменной в Python
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
                                f"Raster shape mismatch: '{var_name}' has shape {arr.shape}, "
                                f"but expected {first_shape} (based on first loaded raster)"
                            )

            # Безопасный контекст для eval (отключаем __builtins__ для защиты)
            eval_globals = {
                "__builtins__": {},
                "np": np,
                "where": np.where,
                "abs": np.abs,
                "exp": np.exp,
                "log": np.log,
                "log10": np.log10,
                "sin": np.sin,
                "cos": np.cos,
                "tan": np.tan,
                "sqrt": np.sqrt,
                "maximum": np.maximum,
                "minimum": np.minimum,
                "pi": np.pi,
                "e": np.e
            }
            # Добавляем массивы переменных
            eval_globals.update(variables)

            # Вычисление выражения
            try:
                result_arr = eval(expression, eval_globals)
            except Exception as e:
                raise ValueError(f"Failed to evaluate expression: {e}")

            # Если результат — скаляр, превращаем его в массив нужной размерности
            if not isinstance(result_arr, np.ndarray):
                result_arr = np.full(first_shape, result_arr, dtype=np.float32)
            else:
                # Приводим к типу float32
                result_arr = result_arr.astype(np.float32)

            # Настройка профиля выходного файла
            first_profile.update(
                dtype=rasterio.float32,
                count=1,
                driver="GTiff"
            )

            # Запись во временный файл
            temp_path = dst_file + ".tmp.tif"
            if os.path.exists(temp_path):
                os.remove(temp_path)

            with rasterio.open(temp_path, "w", **first_profile) as dst:
                dst.write(result_arr, 1)

            # Трансляция во временный COG
            if os.path.exists(dst_file):
                os.remove(dst_file)

            logger.info("Translating algebra result to COG...")
            temp_ds = gdal.Open(temp_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary algebra raster for COG translation")

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

            logger.info("Map algebra calculation successful")

        except Exception as e:
            logger.error(f"Map algebra calculation failed: {e}")
            if 'temp_path' in locals() and os.path.exists(temp_path):
                try:
                    os.remove(temp_path)
                except Exception:
                    pass
            raise RuntimeError(f"Map algebra failed: {e}")
