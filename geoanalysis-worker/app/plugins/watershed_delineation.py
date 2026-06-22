import os
import sys
import numpy as np
from osgeo import gdal, ogr, osr
from pysheds.grid import Grid
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

# Увеличиваем лимит рекурсии для гидрологических алгоритмов
sys.setrecursionlimit(50000)

class WatershedDelineationPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "watershed_delineation"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dem_path = local_inputs.get("dem_raster")
        if not dem_path:
            raise ValueError("Input 'dem_raster' is required for watershed_delineation plugin")

        if "target_point_x" not in params or "target_point_y" not in params:
            raise ValueError("Parameters 'target_point_x' and 'target_point_y' are required for watershed_delineation")

        try:
            target_point_x = float(params["target_point_x"])
            target_point_y = float(params["target_point_y"])
        except (TypeError, ValueError) as e:
            raise ValueError(f"Invalid target point coordinates: must be numbers. Error: {e}")

        threshold = int(params.get("threshold", 1000))

        basin_vector_path = os.path.join(workspace, "basin.geojson")
        streams_raster_path = os.path.join(workspace, "streams.tif")

        logger.info(
            f"Running watershed delineation for {dem_path} at ({target_point_x}, {target_point_y}) "
            f"with stream threshold={threshold}"
        )

        self._delineate(
            dem_path, basin_vector_path, streams_raster_path, 
            target_point_x, target_point_y, threshold, workspace
        )

        return {
            "vector_result": basin_vector_path,
            "raster_result": streams_raster_path
        }

    def _delineate(
        self, dem_path: str, basin_vector_file: str, streams_raster_file: str, 
        target_point_x: float, target_point_y: float, threshold: int, workspace: str
    ):
        gdal.UseExceptions()

        # 1. Сначала проецируем входные координаты из WGS84 в проекцию DEM
        src_ds = gdal.Open(dem_path)
        if src_ds is None:
            raise RuntimeError(f"Could not open DEM raster: {dem_path}")

        try:
            proj_wkt = src_ds.GetProjectionRef()
            if not proj_wkt:
                raise ValueError(f"DEM raster {dem_path} lacks spatial reference (projection) info")

            src_srs = osr.SpatialReference()
            src_srs.ImportFromEPSG(4326)

            target_srs = osr.SpatialReference()
            target_srs.ImportFromWkt(proj_wkt)

            try:
                src_srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
                target_srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
            except AttributeError:
                pass

            tx = osr.CoordinateTransformation(src_srs, target_srs)
            trans_pt = tx.TransformPoint(target_point_x, target_point_y)
            proj_x, proj_y = trans_pt[0], trans_pt[1]

            logger.info(f"Projected pour point: WGS84 ({target_point_x}, {target_point_y}) -> DEM ({proj_x}, {proj_y})")

        finally:
            src_ds = None

        # 2. Гидрологический анализ с помощью pysheds
        logger.info("Initializing pysheds grid from DEM...")
        grid = Grid.from_raster(dem_path, data_name='dem')
        dem = grid.read_raster(dem_path)

        # Кондиционирование рельефа (заполнение депрессий и плоских зон)
        logger.info("Conditioning DEM (filling depressions)...")
        grid.fill_depressions('dem', out_name='flooded_dem')
        logger.info("Resolving flats...")
        grid.resolve_flats('flooded_dem', out_name='inflated_dem')

        # Направления стоков по D8
        logger.info("Calculating flow directions...")
        dirmap = (64, 128, 1, 2, 4, 8, 16, 32)
        grid.flowdir(data='inflated_dem', out_name='dir', dirmap=dirmap)

        # Накопление потока
        logger.info("Calculating flow accumulation...")
        grid.accumulation(data='dir', out_name='acc', dirmap=dirmap)

        # Выделение водосборного бассейна (catchment)
        logger.info("Delineating catchment basin...")
        grid.catchment(
            data='dir', x=proj_x, y=proj_y, dirmap=dirmap, 
            out_name='catch', recursionlimit=30000, xytype='label'
        )

        # 3. Сохранение и векторизация бассейна в GeoJSON
        temp_catch_raster = os.path.join(workspace, "catch_temp.tif")
        if os.path.exists(temp_catch_raster):
            os.remove(temp_catch_raster)
        
        logger.info("Exporting catchment raster...")
        grid.to_raster('catch', temp_catch_raster)

        # Векторизуем catchment raster в GeoJSON полигон
        logger.info("Polygonizing catchment to GeoJSON...")
        self._polygonize_catchment(temp_catch_raster, basin_vector_file, proj_wkt)

        if os.path.exists(temp_catch_raster):
            os.remove(temp_catch_raster)

        # 4. Выделение сети водотоков (streams) по порогу
        logger.info("Extracting stream lines...")
        acc_view = grid.view('acc')
        streams_arr = np.where(acc_view > threshold, 1, 0).astype(np.int8)

        # Добавляем в сетку и экспортируем
        grid.add_gridded_data(streams_arr, 'streams', check_dims=False)
        temp_streams_path = os.path.join(workspace, "streams_temp.tif")
        if os.path.exists(temp_streams_path):
            os.remove(temp_streams_path)

        grid.to_raster('streams', temp_streams_path)

        #  Транслируем streams_temp в COG для отображения на фронтенде
        if os.path.exists(streams_raster_file):
            os.remove(streams_raster_file)

        logger.info("Translating streams raster to COG...")
        temp_ds = gdal.Open(temp_streams_path)
        if temp_ds is None:
            raise RuntimeError("Could not open temporary streams raster for COG translation")

        translate_options = gdal.TranslateOptions(
            format="COG",
            creationOptions=[
                "COMPRESS=DEFLATE",
                "PREDICTOR=2",
                "OVERVIEWS=AUTO",
                "BLOCKSIZE=512"
            ]
        )
        gdal.Translate(streams_raster_file, temp_ds, options=translate_options)
        temp_ds = None

        if os.path.exists(temp_streams_path):
            os.remove(temp_streams_path)

        logger.info("Watershed delineation successfully completed")

    def _polygonize_catchment(self, src_raster: str, dst_vector: str, proj_wkt: str):
        drv = ogr.GetDriverByName("GeoJSON")
        if drv is None:
            raise RuntimeError("GeoJSON OGR Driver not found")

        if os.path.exists(dst_vector):
            drv.DeleteDataSource(dst_vector)

        out_ds = drv.CreateDataSource(dst_vector)
        if out_ds is None:
            raise RuntimeError(f"Could not create output GeoJSON: {dst_vector}")

        srs = osr.SpatialReference()
        srs.ImportFromWkt(proj_wkt)

        out_layer = out_ds.CreateLayer("basin", srs=srs, geom_type=ogr.wkbPolygon)
        
        # Поле val
        field_defn = ogr.FieldDefn("val", ogr.OFTInteger)
        out_layer.CreateField(field_defn)
        field_idx = out_layer.GetLayerDefn().GetFieldIndex("val")

        src_ds = gdal.Open(src_raster)
        if src_ds is None:
            raise RuntimeError(f"Could not open temporary catchment raster: {src_raster}")

        try:
            src_band = src_ds.GetRasterBand(1)
            # Векторизуем только не-нулевые пиксели (catchment = 1)
            gdal.Polygonize(src_band, src_band, out_layer, field_idx, options=[])
        finally:
            src_ds = None
            out_layer = None
            out_ds.FlushCache()
            out_ds = None
