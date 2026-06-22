import os
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class RasterizeVectorPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "rasterize_vector"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        vector_path = local_inputs.get("vector_features")
        template_path = local_inputs.get("template_raster")

        if not vector_path or not template_path:
            raise ValueError("Both 'vector_features' and 'template_raster' inputs are required for rasterize_vector plugin")

        attribute_field = params.get("attribute_field")
        default_value = float(params.get("default_value", 1.0))
        nodata_value = float(params.get("nodata_value", 0.0))

        output_filename = "rasterized.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(
            f"Rasterizing vector {vector_path} using template {template_path} "
            f"(attribute_field: {attribute_field}, default_value: {default_value}, nodata_value: {nodata_value})"
        )

        self._rasterize(vector_path, template_path, output_path, attribute_field, default_value, nodata_value)

        return {"raster_result": output_path}

    def _rasterize(
        self, vector_file: str, template_file: str, dst_file: str, 
        attribute_field: str, default_value: float, nodata_value: float
    ):
        gdal.UseExceptions()

        # 1. Открываем эталонный растр для копирования геопривязки и размеров
        template_ds = gdal.Open(template_file)
        if template_ds is None:
            raise RuntimeError(f"Could not open template raster: {template_file}")

        temp_raster_path = dst_file + ".tmp.tif"
        
        try:
            width = template_ds.RasterXSize
            height = template_ds.RasterYSize
            geotransform = template_ds.GetGeoTransform()
            projection = template_ds.GetProjectionRef()

            # 2. Создаем временный пустой GeoTIFF
            drv = gdal.GetDriverByName("GTiff")
            if drv is None:
                raise RuntimeError("GTiff driver not found")

            if os.path.exists(temp_raster_path):
                os.remove(temp_raster_path)

            # Для качественного выжигания числовых атрибутов используем тип Float32
            out_ds = drv.Create(temp_raster_path, width, height, 1, gdal.GDT_Float32)
            out_ds.SetGeoTransform(geotransform)
            out_ds.SetProjection(projection)

            # Заполняем фоновыми значениями nodata
            band = out_ds.GetRasterBand(1)
            band.Fill(nodata_value)
            band.SetNoDataValue(nodata_value)
            out_ds.FlushCache()

            # 3. Открываем векторные данные
            vector_ds = gdal.OpenEx(vector_file, gdal.OF_VECTOR)
            if vector_ds is None:
                raise RuntimeError(f"Could not open vector source: {vector_file}")

            # Настройка параметров растеризации
            if attribute_field:
                rasterize_options = gdal.RasterizeOptions(attribute=attribute_field)
            else:
                rasterize_options = gdal.RasterizeOptions(burnValues=[default_value])

            logger.info("Executing native GDAL Rasterize...")
            gdal.Rasterize(out_ds, vector_ds, options=rasterize_options)

            # Закрываем и сбрасываем кэш
            out_ds.FlushCache()
            out_ds = None
            vector_ds = None

            # 4. Транслируем временный растр в COG
            if os.path.exists(dst_file):
                os.remove(dst_file)

            logger.info("Translating rasterized output to COG...")
            temp_ds = gdal.Open(temp_raster_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary rasterized file for COG translation")

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

            if os.path.exists(temp_raster_path):
                os.remove(temp_raster_path)

            logger.info("Rasterization successful")

        except Exception as e:
            logger.error(f"Rasterize failed: {e}")
            if os.path.exists(temp_raster_path):
                try:
                    os.remove(temp_raster_path)
                except Exception:
                    pass
            raise RuntimeError(f"Rasterize failed: {e}")
        finally:
            template_ds = None
