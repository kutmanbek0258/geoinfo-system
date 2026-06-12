import importlib
import os
import pkgutil
from typing import Dict, Type
from ..core.base_plugin import GeoWorkerPlugin
from ..core.config import logger

class PluginManager:
    def __init__(self, plugins_package: str = "app.plugins"):
        self.plugins: Dict[str, Type[GeoWorkerPlugin]] = {}
        self.load_plugins(plugins_package)

    def load_plugins(self, package_name: str):
        package = importlib.import_module(package_name)
        # Поддержка рекурсивного поиска в подпапках
        for _, module_name, is_pkg in pkgutil.walk_packages(package.__path__, package_name + "."):
            module = importlib.import_module(module_name)
            
            for attr_name in dir(module):
                attr = getattr(module, attr_name)
                if (isinstance(attr, type) and 
                    issubclass(attr, GeoWorkerPlugin) and 
                    attr is not GeoWorkerPlugin):
                    
                    plugin_instance = attr()
                    name = plugin_instance.plugin_name
                    self.plugins[name] = attr
                    logger.info(f"Loaded plugin: {name} from {module_name}")
        
        logger.info(f"Total plugins loaded: {len(self.plugins)}. Active plugins: {list(self.plugins.keys())}")

    def get_plugin(self, name: str) -> Type[GeoWorkerPlugin]:
        return self.plugins.get(name)
