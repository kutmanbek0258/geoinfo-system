import os
import numpy as np
import rasterio
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class SpectralIndicesPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "spectral_indices"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        index_type = params.get("index_type", "NDVI").upper()
        
        # Определение необходимых каналов для каждого индекса
        required_bands = {
            "NDVI": ["red", "nir"],
            "NDWI": ["green", "nir"],
            "NBR": ["nir", "swir"],
            "NDRE": ["nir", "re"]
        }

        if index_type not in required_bands:
            raise ValueError(f"Unsupported index_type '{index_type}'. Supported: {list(required_bands.keys())}")

        bands = required_bands[index_type]
        
        # Проверка наличия нужных каналов в local_inputs
        for band in bands:
            if band not in local_inputs:
                raise ValueError(f"Input '{band}' is required for index {index_type}")

        output_filename = f"{index_type.lower()}.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(f"Calculating {index_type} using bands: {bands}")
        self._calculate_index(local_inputs, output_path, index_type)

        return {"raster_result": output_path}

    def _calculate_index(self, local_inputs: Dict[str, str], dst_file: str, index_type: str):
        gdal.UseExceptions()

        # Открываем исходные каналы с помощью rasterio
        try:
            if index_type == "NDVI":
                red_path = local_inputs["red"]
                nir_path = local_inputs["nir"]
                
                with rasterio.open(red_path) as src_red, rasterio.open(nir_path) as src_nir:
                    red = src_red.read(1).astype(np.float32)
                    nir = src_nir.read(1).astype(np.float32)
                    
                    profile = src_red.profile.copy()
                    
                    # Расчет индекса
                    denominator = nir + red
                    # Защита от деления на ноль
                    index_arr = np.where(denominator != 0.0, (nir - red) / denominator, 0.0)
                    
            elif index_type == "NDWI":
                green_path = local_inputs["green"]
                nir_path = local_inputs["nir"]
                
                with rasterio.open(green_path) as src_green, rasterio.open(nir_path) as src_nir:
                    green = src_green.read(1).astype(np.float32)
                    nir = src_nir.read(1).astype(np.float32)
                    
                    profile = src_green.profile.copy()
                    
                    denominator = green + nir
                    index_arr = np.where(denominator != 0.0, (green - nir) / denominator, 0.0)
                    
            elif index_type == "NBR":
                nir_path = local_inputs["nir"]
                swir_path = local_inputs["swir"]
                
                with rasterio.open(nir_path) as src_nir, rasterio.open(swir_path) as src_swir:
                    nir = src_nir.read(1).astype(np.float32)
                    swir = src_swir.read(1).astype(np.float32)
                    
                    profile = src_nir.profile.copy()
                    
                    denominator = nir + swir
                    index_arr = np.where(denominator != 0.0, (nir - swir) / denominator, 0.0)
                    
            elif index_type == "NDRE":
                nir_path = local_inputs["nir"]
                re_path = local_inputs["re"]
                
                with rasterio.open(nir_path) as src_nir, rasterio.open(re_path) as src_re:
                    nir = src_nir.read(1).astype(np.float32)
                    re_val = src_re.read(1).astype(np.float32)
                    
                    profile = src_nir.profile.copy()
                    
                    denominator = nir + re_val
                    index_arr = np.where(denominator != 0.0, (nir - re_val) / denominator, 0.0)

            # Настройка профиля выходного файла
            profile.update(
                dtype=rasterio.float32,
                count=1,
                driver="GTiff"
            )

            # Сохранение во временный файл
            temp_path = dst_file + ".tmp.tif"
            if os.path.exists(temp_path):
                os.remove(temp_path)

            with rasterio.open(temp_path, "w", **profile) as dst:
                dst.write(index_arr, 1)

            # Трансляция временного растра в COG
            if os.path.exists(dst_file):
                os.remove(dst_file)

            logger.info(f"Translating {index_type} result to COG...")
            temp_ds = gdal.Open(temp_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary index raster for COG translation")

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

            logger.info(f"{index_type} calculation successfully completed")

        except Exception as e:
            logger.error(f"Failed to calculate spectral index {index_type}: {e}")
            if 'temp_path' in locals() and os.path.exists(temp_path):
                try:
                    os.remove(temp_path)
                except Exception:
                    pass
            raise RuntimeError(f"Spectral index calculation failed: {e}")
