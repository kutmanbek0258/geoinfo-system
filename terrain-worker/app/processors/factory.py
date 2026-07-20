from kafka import KafkaProducer
from .base import BaseProcessor
from .terrain_mesh import TerrainMeshProcessor
from .tiles_3d import Tiles3DProcessor

def get_processor(task_type: str, producer: KafkaProducer) -> BaseProcessor:
    if task_type == "TERRAIN_MESH":
        return TerrainMeshProcessor(producer)
    elif task_type in ("3D_TILES", "CITYGML"):
        return Tiles3DProcessor(producer)
    return None
