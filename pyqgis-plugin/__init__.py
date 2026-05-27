def classFactory(iface):
    from .main import GeoInfoSystemConnector
    return GeoInfoSystemConnector(iface)
