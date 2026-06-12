import os
import subprocess
from ..core.config import logger

def convert_to_cog(input_path: str, output_path: str):
    """
    Конвертирует растровый файл в Cloud Optimized GeoTIFF (COG).
    Использует GDAL CLI для надежности и оптимальной производительности.
    """
    logger.info(f"Converting {input_path} to COG...")
    
    # Команда для создания COG через драйвер COG (доступен в GDAL 3.1+)
    cmd = [
        "gdal_translate",
        input_path,
        output_path,
        "-of", "COG",
        "-co", "COMPRESS=DEFLATE",
        "-co", "PREDICTOR=2",
        "-co", "NUM_THREADS=ALL_CPUS"
    ]
    
    try:
        subprocess.run(cmd, check=True, capture_output=True)
        logger.info(f"Successfully created COG: {output_path}")
        return output_path
    except subprocess.CalledProcessError as e:
        logger.error(f"Failed to convert to COG: {e.stderr.decode()}")
        # Если не получилось сделать COG, возвращаем оригинал (fallback)
        return input_path
