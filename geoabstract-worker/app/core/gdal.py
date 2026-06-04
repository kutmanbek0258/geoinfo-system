import os
import json
import subprocess
import re
from typing import List, Optional, Tuple, Dict, Any
from .config import logger, GDAL_CACHEMAX, DEFAULT_WEB_RGB_MAX

def run_command(cmd: List[str], cwd: Optional[str] = None) -> str:
    logger.info("Running command: %s", " ".join(cmd))
    env = os.environ.copy()
    env["GDAL_CACHEMAX"] = GDAL_CACHEMAX

    result = subprocess.run(
        cmd,
        cwd=cwd,
        capture_output=True,
        text=True,
        env=env,
    )

    if result.stdout:
        logger.info("stdout:\n%s", result.stdout.strip())
    if result.stderr:
        logger.info("stderr:\n%s", result.stderr.strip())

    if result.returncode != 0:
        raise RuntimeError(
            "Command failed ({0}): {1}\n{2}".format(
                result.returncode,
                " ".join(cmd),
                result.stderr,
            )
        )

    return result.stdout

def get_band_stats(path: str, band_index: int = 1) -> Tuple[Optional[float], Optional[float]]:
    try:
        out = run_command(["gdalinfo", "-stats", "-json", path])
        info = json.loads(out)
        bands = info.get("bands", [])
        if 1 <= band_index <= len(bands):
            band = bands[band_index - 1]
            min_val = band.get("minimum") or band.get("computedMin") or band.get("approximateMinimum")
            max_val = band.get("maximum") or band.get("computedMax") or band.get("approximateMaximum")
            if min_val is not None and max_val is not None:
                return float(min_val), float(max_val)
    except Exception:
        pass

    try:
        out = run_command(["gdalinfo", "-stats", path])
        current_band = 0
        seen_target = False
        for line in out.splitlines():
            if line.startswith("Band "):
                current_band += 1
                seen_target = (current_band == band_index)
                continue
            if not seen_target:
                continue
            m = re.search(r"Minimum=([-\d\.eE]+),\s*Maximum=([-\d\.eE]+)", line)
            if m:
                return float(m.group(1)), float(m.group(2))
    except Exception:
        pass

    return None, None

def safe_scale_range(min_val: Optional[float], max_val: Optional[float], default_max: float = DEFAULT_WEB_RGB_MAX) -> Tuple[float, float]:
    if max_val is None and min_val is None:
        return 0.0, default_max
    if min_val is None:
        min_val = 0.0
    if max_val is None:
        max_val = default_max
    min_val = max(0.0, float(min_val))
    max_val = float(max_val)
    if max_val <= min_val:
        max_val = min_val + 1.0
    return min_val, max_val

def build_final_cog(
    source_path: str,
    output_path: str,
    render_mode: str,
    scale_ranges: Optional[List[Tuple[float, float]]] = None,
    is_float_index: bool = False,
) -> None:
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    cmd = ["gdal_translate", source_path, output_path, "-of", "COG"]

    if render_mode == "web_rgb":
        cmd.extend(["-ot", "Byte"])
        if scale_ranges:
            for idx, (src_min, src_max) in enumerate(scale_ranges[:3], start=1):
                cmd.extend([
                    "-scale_{0}".format(idx),
                    str(src_min),
                    str(src_max),
                    "0",
                    "255",
                ])
        else:
            for idx in range(1, 4):
                cmd.extend([
                    "-scale_{0}".format(idx),
                    "0",
                    str(DEFAULT_WEB_RGB_MAX),
                    "0",
                    "255",
                ])
    elif is_float_index:
        cmd.extend(["-ot", "Float32"])

    cmd.extend([
        "-co", "COMPRESS=DEFLATE",
        "-co", "PREDICTOR=2",
        "-co", "NUM_THREADS=ALL_CPUS",
        "-co", "BIGTIFF=YES",
    ])

    run_command(cmd)
    try:
        run_command(["gdal_edit.py", "-stats", output_path])
    except Exception as e:
        logger.warning("Could not write stats for %s: %s", output_path, e)

def get_wgs84_extent(path: str) -> Optional[Dict[str, Any]]:
    try:
        out = run_command(["gdalinfo", "-json", path])
        info = json.loads(out)
        return info.get("wgs84Extent")
    except Exception as e:
        logger.warning("Could not get WGS84 extent for %s: %s", path, e)
        return None
