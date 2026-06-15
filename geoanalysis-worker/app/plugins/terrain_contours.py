import os
from osgeo import gdal, ogr, osr
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class TerrainContoursPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "terrain_contours"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dem_path = local_inputs.get("dem_file")
        if not dem_path:
            raise ValueError("Input 'dem_file' is required for terrain_contours plugin")

        interval = float(params.get("interval", 10.0))
        base = float(params.get("base", 0.0))
        elev_field = params.get("attribute", "elev")
        use_3d = bool(params.get("use_3d", False))
        output_format = params.get("format", "GeoJSON")

        output_filename = f"contours_{int(interval)}m"

        # Расширенная поддержка форматов из системного реестра (GeoJSON / GeoPackage)
        if output_format.lower() == "geojson":
            extension = "geojson"
        elif output_format.lower() == "gpkg":
            extension = "gpkg"
        else:
            extension = "shp"

        output_path = os.path.join(workspace, f"{output_filename}.{extension}")

        logger.info(f"Generating contours for {dem_path} with interval {interval}, 3D={use_3d}")

        self._generate_contours(dem_path, output_path, interval, base, elev_field, use_3d, output_format)

        return {"vector_result": output_path}

    def _generate_contours(self, src_file, dst_file, interval, base, elev_field, use_3d, output_format):
        gdal.UseExceptions()

        src_ds = gdal.Open(src_file)
        if src_ds is None:
            raise RuntimeError(f"Could not open {src_file}")

        src_band = src_ds.GetRasterBand(1)

        # Выбираем корректный OGR драйвер на основе формата конфигурации
        if output_format.lower() == "geojson":
            driver_name = "GeoJSON"
        elif output_format.lower() == "gpkg":
            driver_name = "GPKG"
        else:
            driver_name = "ESRI Shapefile"

        drv = ogr.GetDriverByName(driver_name)
        if drv is None:
            raise RuntimeError(f"OGR Driver '{driver_name}' not found.")

        # Очищаем старый файл, если воркер перезапустил задачу в той же папке
        if os.path.exists(dst_file):
            drv.DeleteDataSource(dst_file)

        # Создаем выходной файл напрямую в tmpfs воркспейса
        out_ds = drv.CreateDataSource(dst_file)
        if out_ds is None:
            raise RuntimeError(f"Could not create output dataset: {dst_file}")

        srs = osr.SpatialReference()
        srs.ImportFromWkt(src_ds.GetProjectionRef())

        geom_type = ogr.wkbLineString25D if use_3d else ogr.wkbLineString

        # Имя слоя внутри векторного контейнера
        layer_name = os.path.splitext(os.path.basename(dst_file))[0]
        out_layer = out_ds.CreateLayer(layer_name, srs=srs, geom_type=geom_type)

        # Создаем атрибутивное поле для высоты
        field_defn = ogr.FieldDefn(elev_field, ogr.OFTReal)
        out_layer.CreateField(field_defn)
        elev_field_idx = out_layer.GetLayerDefn().GetFieldIndex(elev_field)

        logger.info("Executing native GDAL ContourGenerate directly to output path...")

        # Запускаем нативную генерацию прямо в целевой файл
        gdal.ContourGenerate(src_band, interval, base, [], 0, 0, out_layer, -1, elev_field_idx)

        # КРИТИЧЕСКИ ВАЖНО: Уничтожаем ссылки и сбрасываем кэш на диск,
        # чтобы закрыть дескрипторы файлов перед отправкой артефактов в S3
        out_layer = None
        out_ds.FlushCache()
        out_ds = None
        src_ds = None

        logger.info(f"Contours saved successfully to {dst_file}")