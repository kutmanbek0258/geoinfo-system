from kafka import KafkaProducer
from .base import BaseProcessor
from .terrain_mesh import TerrainMeshProcessor

def get_processor(task_type: str, producer: KafkaProducer) -> BaseProcessor:
    if task_type == "TERRAIN_MESH":
        return TerrainMeshProcessor(producer)
    return None
