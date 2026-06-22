import os
import numpy as np
import rasterio
from sklearn.cluster import KMeans
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class UnsupervisedClassPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "unsupervised_class"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        raster_path = local_inputs.get("multiband_raster")
        if not raster_path:
            raise ValueError("Input 'multiband_raster' is required for unsupervised_class plugin")

        clusters_count = int(params.get("clusters_count", 5))
        max_iter = int(params.get("max_iter", 100))

        output_filename = "classified.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(
            f"Classifying raster {raster_path} using K-Means (clusters: {clusters_count}, max_iter: {max_iter})"
        )

        self._classify_kmeans(raster_path, output_path, clusters_count, max_iter)

        return {"raster_result": output_path}

    def _classify_kmeans(self, src_file: str, dst_file: str, clusters_count: int, max_iter: int):
        gdal.UseExceptions()

        try:
            with rasterio.open(src_file) as src:
                # Читаем все каналы: (bands, height, width)
                data = src.read()
                profile = src.profile.copy()
                
                bands, height, width = data.shape
                # Превращаем в (pixels, bands)
                data_2d = data.reshape(bands, -1).T
                
                # Исключаем пиксели, где хотя бы в одном канале есть nodata или NaN
                nodata = src.nodata
                if nodata is not None:
                    mask = ~np.any(data == nodata, axis=0)
                else:
                    mask = ~np.any(np.isnan(data), axis=0)
                
                mask_flat = mask.flatten()
                valid_pixels = data_2d[mask_flat]

                if len(valid_pixels) == 0:
                    raise ValueError("No valid pixels found in the input raster for classification")

                logger.info(f"Running K-Means model on {len(valid_pixels)} valid pixels...")
                kmeans = KMeans(
                    n_clusters=clusters_count, 
                    max_iter=max_iter, 
                    random_state=42, 
                    n_init='auto'
                )
                labels = kmeans.fit_predict(valid_pixels)

                # Восстанавливаем форму растра. Пикселям nodata даем значение -1
                out_labels = np.full(mask_flat.shape, -1, dtype=np.int8)
                out_labels[mask_flat] = labels
                out_labels_2d = out_labels.reshape((height, width))

                # Обновляем профиль под классификацию (8-битный целочисленный растр)
                profile.update(
                    dtype=rasterio.int8,
                    count=1,
                    driver="GTiff",
                    nodata=-1
                )

                temp_path = dst_file + ".tmp.tif"
                if os.path.exists(temp_path):
                    os.remove(temp_path)

                with rasterio.open(temp_path, "w", **profile) as dst:
                    dst.write(out_labels_2d, 1)

            # Трансляция в COG
            if os.path.exists(dst_file):
                os.remove(dst_file)

            logger.info("Translating classification result to COG...")
            temp_ds = gdal.Open(temp_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary classification raster for COG translation")

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

            logger.info("K-Means classification completed successfully")

        except Exception as e:
            logger.error(f"Classification failed: {e}")
            if 'temp_path' in locals() and os.path.exists(temp_path):
                try:
                    os.remove(temp_path)
                except Exception:
                    pass
            raise RuntimeError(f"K-Means classification failed: {e}")
