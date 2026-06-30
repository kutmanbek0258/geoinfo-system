import os
import json
from osgeo import gdal, osr
from typing import Dict, Any, Optional
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class ViewshedAnalysisDynamicPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "viewshed_analysis_dynamic"

    def get_schema(self) -> Optional[dict]:
        schema_path = os.path.join(os.path.dirname(__file__), "viewshed_analysis_dynamic.schema.json")
        with open(schema_path, "r", encoding="utf-8") as f:
            return json.load(f)

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dem_path = local_inputs.get("dem_file")
        if not dem_path:
            raise ValueError("Input 'dem_file' is required for viewshed_analysis_dynamic plugin")

        # Получение координат точки наблюдателя из сложного параметра observer_point
        observer_point = params.get("observer_point", {})
        if not observer_point or "x" not in observer_point or "y" not in observer_point:
            raise ValueError("Parameter 'observer_point' with 'x' and 'y' properties is required")

        try:
            observer_x = float(observer_point["x"])
            observer_y = float(observer_point["y"])
        except (TypeError, ValueError) as e:
            raise ValueError(f"Invalid observer coordinates: x and y must be numbers. Error: {e}")

        observer_height = float(params.get("observer_height", 2.0))
        target_height = float(params.get("target_height", 0.0))
        max_distance = float(params.get("max_distance", 10000.0))

        output_filename = "viewshed.tif"
        output_path = os.path.join(workspace, output_filename)

        logger.info(
            f"Running viewshed dynamic analysis for {dem_path} at ({observer_x}, {observer_y}) "
            f"with observer_height={observer_height}, target_height={target_height}, max_distance={max_distance}"
        )

        self._generate_viewshed(
            dem_path, output_path, observer_x, observer_y, observer_height, target_height, max_distance
        )

        return {"raster_result": output_path}

    def _generate_viewshed(
        self, src_file, dst_file, observer_x, observer_y, observer_height, target_height, max_distance
    ):
        gdal.UseExceptions()

        src_ds = gdal.Open(src_file)
        if src_ds is None:
            raise RuntimeError(f"Could not open source DEM file: {src_file}")

        try:
            proj_wkt = src_ds.GetProjectionRef()
            if not proj_wkt:
                raise ValueError(f"DEM file {src_file} lacks spatial reference (projection) info")

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
            trans_pt = tx.TransformPoint(observer_x, observer_y)
            proj_x, proj_y = trans_pt[0], trans_pt[1]

            logger.info(f"Transformed observer coordinates from WGS84 ({observer_x}, {observer_y}) to DEM projection ({proj_x}, {proj_y})")

            src_band = src_ds.GetRasterBand(1)
            temp_viewshed_path = dst_file + ".tmp.tif"

            if os.path.exists(temp_viewshed_path):
                try:
                    os.remove(temp_viewshed_path)
                except Exception:
                    pass

            mode_enum = getattr(gdal, 'GVM_Normal', 1)
            if hasattr(gdal, 'ViewshedMode'):
                mode_enum = getattr(gdal.ViewshedMode, 'NORMAL', mode_enum)

            logger.info("Executing native GDAL ViewshedGenerate...")
            gdal.ViewshedGenerate(
                srcBand=src_band,
                driverName="GTiff",
                targetRasterName=temp_viewshed_path,
                creationOptions=["COMPRESS=DEFLATE"],
                observerX=proj_x,
                observerY=proj_y,
                observerHeight=observer_height,
                targetHeight=target_height,
                visibleVal=255,
                invisibleVal=0,
                outOfRangeVal=0,
                noDataVal=0,
                dfCurvCoeff=1.0 - 1.0/7.0,
                mode=mode_enum,
                maxDistance=max_distance
            )

            if os.path.exists(dst_file):
                try:
                    os.remove(dst_file)
                except Exception:
                    pass

            logger.info("Translating temporary viewshed to COG format...")
            temp_ds = gdal.Open(temp_viewshed_path)
            if temp_ds is None:
                raise RuntimeError("Could not open temporary viewshed raster for COG translation")

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

            if os.path.exists(temp_viewshed_path):
                os.remove(temp_viewshed_path)

            logger.info(f"Viewshed analysis successful, saved to {dst_file}")

        except Exception as e:
            logger.error(f"GDAL ViewshedGenerate failed: {e}")
            if 'temp_viewshed_path' in locals() and os.path.exists(temp_viewshed_path):
                try:
                    os.remove(temp_viewshed_path)
                except Exception:
                    pass
            raise RuntimeError(f"Failed to generate viewshed: {e}")
        finally:
            src_ds = None
