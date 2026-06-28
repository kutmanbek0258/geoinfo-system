import os
from osgeo import gdal
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class ImportDxfPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "import_dxf"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        dxf_path = local_inputs.get("dxf_file")
        if not dxf_path:
            raise ValueError("Input 'dxf_file' is required for import_dxf plugin")

        source_crs = params.get("source_crs")
        
        # 1. Открываем DXF файл через GDAL/OGR
        ds = gdal.OpenEx(dxf_path, gdal.OF_VECTOR)
        if ds is None:
            raise RuntimeError(f"Could not open DXF file: {dxf_path}")

        # 2. Проверяем геопривязку (SRS) слоев во входном файле
        has_srs = False
        for i in range(ds.GetLayerCount()):
            layer = ds.GetLayer(i)
            srs = layer.GetSpatialRef()
            if srs is not None:
                has_srs = True
                break

        src_srs_opt = None
        if not has_srs:
            logger.warning("DXF file does not contain spatial reference system (CRS).")
            if not source_crs:
                raise ValueError(
                    "Входной DXF файл не содержит геопривязки (CRS). "
                    "Необходимо указать систему координат источника (source_crs), например EPSG:32643, EPSG:3857."
                )
            src_srs_opt = source_crs
            logger.info(f"Using user-specified source CRS: {src_srs_opt}")

        output_filename = "imported_vector.geojson"
        output_path = os.path.join(workspace, output_filename)

        # 3. Конвертируем DXF в 3D GeoJSON (EPSG:4326)
        logger.info(f"Translating DXF to GeoJSON in EPSG:4326 (preserving Z-dimension)...")
        options = gdal.VectorTranslateOptions(
            format="GeoJSON",
            srcSRS=src_srs_opt,
            dstSRS="EPSG:4326",
            reproject=True,
            dim="XYZ" # Принудительно сохраняем Z-координату (высоты/глубины)
        )
        
        try:
            gdal.VectorTranslate(output_path, ds, options=options)
            logger.info("DXF import completed successfully")
        except Exception as e:
            raise RuntimeError(f"GDAL VectorTranslate failed: {e}")

        return {"vector_result": output_path}
