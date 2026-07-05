from kafka import KafkaProducer
from .base import BaseProcessor
from .sentinel2 import Sentinel2Processor
from .landsat8 import Landsat8Processor
from .raw_raster import RawRasterProcessor
from .verifier import VerifierProcessor
from .netcdf import NetcdfProcessor

def get_processor(task_type: str, producer: KafkaProducer) -> BaseProcessor:
    if task_type == "SENTINEL_COG":
        return Sentinel2Processor(producer)
    elif task_type == "LANDSAT_COG":
        return Landsat8Processor(producer)
    elif task_type == "RAW_GEOTIFF_OPTIMIZE":
        return RawRasterProcessor(producer)
    elif task_type == "NETCDF_COG":
        return NetcdfProcessor(producer)
    elif task_type == "VERIFY_FILE":
        return VerifierProcessor(producer)
    return None
