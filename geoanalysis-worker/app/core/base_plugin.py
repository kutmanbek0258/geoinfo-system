from abc import ABC, abstractmethod
from typing import Dict, Any, Optional

class GeoWorkerPlugin(ABC):
    @property
    @abstractmethod
    def plugin_name(self) -> str:
        """
        Идентификатор плагина (соответствует полю pluginName в Kafka)
        """
        pass

    @abstractmethod
    def run(self, local_inputs: Dict[str, str], params: Dict[str, Any], workspace: str) -> Dict[str, str]:
        """
        Выполнение логики плагина.
        :param local_inputs: Локальные пути к файлам в tmpfs.
        :param params: Бизнес-параметры задачи.
        :param workspace: Путь к tmpfs-директории для сохранения результатов.
        :return: Словарь локальных путей к созданным артефактам.
        """
        pass

    def get_schema(self) -> Optional[dict]:
        """
        Возвращает схему UI плагина. По умолчанию None (системный плагин).
        """
        return None
