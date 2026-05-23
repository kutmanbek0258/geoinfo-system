import os
import json
import shutil
import tempfile
from typing import Any, Dict
from .base import BaseProcessor
from ..core.config import logger, BASE_DIR, CTB_FORMAT, CTB_ZOOM, GDAL_WARP_SRS
from ..core.clients import minio_client
from ..core.gdal import run_command, get_elevation_band_index

class TerrainMeshProcessor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        output_prefix = job_data.get("outputPrefix")
        job_id = str(job_data.get("jobId"))
        
        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")

        final_output_path = os.path.join(BASE_DIR, output_prefix)
        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]

        work_dir = tempfile.mkdtemp(prefix=f"terrain-{job_id}-")
        input_file = os.path.join(work_dir, "input.tif")
        normalized_file = os.path.join(work_dir, "normalized.tif")
        vrt_file = os.path.join(work_dir, "tiles.vrt")
        temp_tiles_dir = os.path.join(work_dir, "terrain")

        try:
            self.send_status(job_id, "PROCESSING", "TERRAIN_MESH", output_prefix=output_prefix)
            
            logger.info("Downloading raw DEM GeoTIFF: %s/%s", source_bucket, source_key)
            minio_client.fget_object(source_bucket, source_key, input_file)
            
            elevation_band_idx = get_elevation_band_index(input_file)
            if elevation_band_idx is None:
                raise RuntimeError("The provided file does not contain an elevation band (DEM)")

            extracted_dem = os.path.join(work_dir, "extracted_dem.tif")
            logger.info("Extracting elevation band %s", elevation_band_idx)
            run_command(["gdal_translate", "-b", str(elevation_band_idx), input_file, extracted_dem, "-co", "COMPRESS=DEFLATE"])
            
            converted_file = os.path.join(work_dir, "converted.tif")
            run_command(["gdal_translate", extracted_dem, converted_file, "-co", "TILED=YES", "-co", "COMPRESS=DEFLATE", "-co", "BIGTIFF=YES"])
            
            run_command(["gdalwarp", "-t_srs", GDAL_WARP_SRS, "-dstnodata", "-9999", "-multi", "-overwrite", converted_file, normalized_file])
            run_command(["gdal_translate", "-of", "VRT", normalized_file, vrt_file])

            os.makedirs(temp_tiles_dir, exist_ok=True)
            logger.info("Generating mesh tiles using ctb-tile")
            run_command(["ctb-tile", "-f", CTB_FORMAT, "-C", "-N", "-z", CTB_ZOOM, "-o", temp_tiles_dir, vrt_file])
            run_command(["ctb-tile", "-f", CTB_FORMAT, "-C", "-N", "-l", "-z", CTB_ZOOM, "-o", temp_tiles_dir, vrt_file])
            
            self.fix_layer_json(os.path.join(temp_tiles_dir, "layer.json"))

            if self.count_generated_tiles(temp_tiles_dir) == 0:
                raise RuntimeError("No .terrain tiles were generated")

            logger.info("Saving generated tiles to terrain store: %s", final_output_path)
            self.save_tree(temp_tiles_dir, final_output_path)
            
            self.send_status(job_id, "READY", "TERRAIN_MESH", output_prefix=output_prefix)
            logger.info("Terrain job %s completed successfully", job_id)
            
        except Exception as e:
            logger.exception("Terrain job %s failed", job_id)
            self.send_status(job_id, "FAILED", "TERRAIN_MESH", error_message=str(e), output_prefix=output_prefix)
        finally:
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        output_prefix = job_data.get("outputPrefix")
        if not output_prefix: return

        target = os.path.join(BASE_DIR, output_prefix)
        logger.info("Deleting terrain mesh data at %s", target)
        try:
            if os.path.isdir(target):
                shutil.rmtree(target)
            elif os.path.isfile(target):
                os.remove(target)
        except Exception:
            logger.exception("Failed to delete %s", target)

    def save_tree(self, src_dir: str, dst_dir: str) -> None:
        if os.path.exists(dst_dir):
            shutil.rmtree(dst_dir)
        os.makedirs(os.path.dirname(dst_dir), exist_ok=True)
        shutil.copytree(src_dir, dst_dir)

    def fix_layer_json(self, layer_json_path: str) -> None:
        if not os.path.exists(layer_json_path):
            raise RuntimeError("layer.json was not generated")
        with open(layer_json_path, "r", encoding="utf-8") as f:
            layer_json = json.load(f)
        if "schema" in layer_json and "scheme" not in layer_json:
            layer_json["scheme"] = layer_json.pop("schema")
        layer_json["format"] = "quantized-mesh-1.0"
        layer_json["tiles"] = ["{z}/{x}/{y}.terrain"]
        with open(layer_json_path, "w", encoding="utf-8") as f:
            json.dump(layer_json, f, ensure_ascii=False, indent=2)

    def count_generated_tiles(self, output_dir: str) -> int:
        total = 0
        for root, _, files in os.walk(output_dir):
            for f in files:
                if f.endswith(".terrain"):
                    total += 1
        return total
