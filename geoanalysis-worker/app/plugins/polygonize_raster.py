import os
from osgeo import gdal, ogr, osr
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class PolygonizeRasterPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "polygonize_raster"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        raster_path = local_inputs.get("classified_raster")
        if not raster_path:
            raise ValueError("Input 'classified_raster' is required for polygonize_raster plugin")

        connectivity = int(params.get("connectivity", 4))
        mask_zero = bool(params.get("mask_zero", True))
        output_format = params.get("format", "GeoJSON")

        if output_format.lower() == "geojson":
            extension = "geojson"
            driver_name = "GeoJSON"
        elif output_format.lower() == "gpkg":
            extension = "gpkg"
            driver_name = "GPKG"
        else:
            extension = "geojson"
            driver_name = "GeoJSON"

        output_filename = "polygonized." + extension
        output_path = os.path.join(workspace, output_filename)

        logger.info(
            f"Polygonizing raster {raster_path} (connectivity: {connectivity}, mask_zero: {mask_zero}, format: {output_format})"
        )

        self._polygonize(raster_path, output_path, connectivity, mask_zero, driver_name)

        return {"vector_result": output_path}

    def _polygonize(self, src_file: str, dst_file: str, connectivity: int, mask_zero: bool, driver_name: str):
        gdal.UseExceptions()

        src_ds = gdal.Open(src_file)
        if src_ds is None:
            raise RuntimeError(f"Could not open source raster: {src_file}")

        try:
            src_band = src_ds.GetRasterBand(1)
            
            # Настройка драйвера OGR
            drv = ogr.GetDriverByName(driver_name)
            if drv is None:
                raise RuntimeError(f"OGR Driver '{driver_name}' not found")

            # Очищаем старый файл, если он остался от прошлых запусков
            if os.path.exists(dst_file):
                drv.DeleteDataSource(dst_file)

            out_ds = drv.CreateDataSource(dst_file)
            if out_ds is None:
                raise RuntimeError(f"Could not create vector datasource: {dst_file}")

            # Читаем проекцию растра
            srs = osr.SpatialReference()
            srs.ImportFromWkt(src_ds.GetProjectionRef())

            # Имя слоя внутри векторного контейнера
            layer_name = os.path.splitext(os.path.basename(dst_file))[0]
            out_layer = out_ds.CreateLayer(layer_name, srs=srs, geom_type=ogr.wkbPolygon)

            # Создаем атрибутивное поле для значений пикселей
            field_defn = ogr.FieldDefn("val", ogr.OFTInteger)
            out_layer.CreateField(field_defn)
            field_idx = out_layer.GetLayerDefn().GetFieldIndex("val")

            # Маскирующий канал
            mask_band = src_band if mask_zero else None

            # Опции
            options = []
            if connectivity == 8:
                options.append("8connected=8")

            logger.info("Executing native GDAL Polygonize...")
            gdal.Polygonize(src_band, mask_band, out_layer, field_idx, options=options)

            # КРИТИЧЕСКИ ВАЖНО: Закрываем дескрипторы для сброса кэша
            out_layer = None
            out_ds.FlushCache()
            out_ds = None

            logger.info("Polygonization completed successfully")

        except Exception as e:
            logger.error(f"Polygonize failed: {e}")
            raise RuntimeError(f"Raster polygonization failed: {e}")
        finally:
            src_ds = None
