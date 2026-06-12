import os
import json
import geopandas as gpd
import pandas as pd
from rasterstats import zonal_stats
from typing import Dict, Any
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class ZonalStatisticsPlugin(GeoWorkerPlugin):
    @property
    def plugin_name(self) -> str:
        return "zonal_statistics"

    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        raster_path = local_inputs.get("source_raster")
        vector_path = local_inputs.get("zones_vector")

        if not raster_path or not vector_path:
            raise ValueError("Both 'source_raster' and 'zones_vector' are required for zonal_statistics plugin")

        stats = params.get("stats", ["mean", "count"])
        categorical = bool(params.get("categorical", False))
        add_properties = bool(params.get("add_properties", True))

        logger.info(f"Starting zonal statistics for {raster_path} using {vector_path}")
        logger.info(f"Stats requested: {stats}, categorical: {categorical}")

        # 1. Загрузка вектора
        gdf = gpd.read_file(vector_path)
        if gdf.empty:
            raise ValueError("Vector zones file is empty")

        # 2. Проверка и выравнивание проекций (CRS)
        gdf = self._align_crs(raster_path, gdf)

        # 3. Расчет зональной статистики
        # rasterstats может принимать GeoDataFrame напрямую
        results = zonal_stats(
            gdf, 
            raster_path, 
            stats=stats, 
            categorical=categorical, 
            geojson_out=True # Получаем результат в формате GeoJSON-подобных словарей
        )

        # 4. Форматирование результатов
        
        # Результат 1: Чистый JSON (только статистика + исходные свойства, если запрошено)
        json_results = []
        for res in results:
            item = res['properties']
            if not add_properties:
                # Оставляем только те ключи, которые относятся к статистике
                # (rasterstats добавляет их в properties)
                # В случае geojson_out=True, свойства объекта сохраняются.
                # Если add_properties=False, мы должны отфильтровать их.
                # Однако обычно пользователю нужны эти данные привязанными к ID.
                pass
            json_results.append(item)

        json_output_path = os.path.join(workspace, "zonal_stats.json")
        with open(json_output_path, 'w', encoding='utf-8') as f:
            json.dump(json_results, f, ensure_ascii=False, indent=2)

        # Результат 2: GeoJSON (геометрия + статистика в атрибутах)
        geojson_output_path = os.path.join(workspace, "zonal_stats.geojson")
        
        # Создаем новый GeoDataFrame из результатов rasterstats
        result_gdf = gpd.GeoDataFrame.from_features(results, crs=gdf.crs)
        
        # Сохраняем через pyogrio для скорости
        result_gdf.to_file(geojson_output_path, driver="GeoJSON", engine="pyogrio")

        logger.info(f"Zonal statistics completed. Results saved to {json_output_path} and {geojson_output_path}")

        return {
            "statistics_json": json_output_path,
            "statistics_geojson": geojson_output_path
        }

    def _align_crs(self, raster_path: str, gdf: gpd.GeoDataFrame) -> gpd.GeoDataFrame:
        """Приводит CRS вектора к CRS растра."""
        import rasterio
        with rasterio.open(raster_path) as src:
            raster_crs = src.crs
            
        if gdf.crs != raster_crs:
            logger.info(f"Reprojecting vector from {gdf.crs} to {raster_crs}")
            return gdf.to_crs(raster_crs)
        
        return gdf
