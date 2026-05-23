import os
import json
import subprocess
from typing import List, Optional
from .config import logger, GDAL_CACHEMAX

def run_command(cmd: List[str], cwd: Optional[str] = None) -> str:
    logger.info("Running command: %s", " ".join(cmd))
    env = os.environ.copy()
    env["GDAL_CACHEMAX"] = GDAL_CACHEMAX

    result = subprocess.run(
        cmd,
        cwd=cwd,
        capture_output=True,
        text=True,
        env=env
    )

    if result.stdout:
        logger.info("stdout:\n%s", result.stdout.strip())

    if result.stderr:
        logger.info("stderr:\n%s", result.stderr.strip())

    if result.returncode != 0:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {' '.join(cmd)}\n{result.stderr}"
        )
    
    return result.stdout

def get_elevation_band_index(file_path: str) -> Optional[int]:
    try:
        info_output = run_command(["gdalinfo", "-json", file_path])
        info = json.loads(info_output)
        bands = info.get("bands", [])
        if not bands: return None
        for i, band in enumerate(bands):
            color_interp = band.get("colorInterpretation", "Undefined")
            if color_interp not in ["Red", "Green", "Blue", "Alpha", "Palette"]:
                return i + 1
        return None
    except Exception:
        return None
