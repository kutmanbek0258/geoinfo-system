import json
from qgis.core import (
    QgsProject,
    QgsRasterLayer,
    QgsVectorLayer,
    QgsFeature,
    QgsGeometry,
    QgsField,
    QgsFields,
    QgsMessageLog,
    Qgis,
    QgsJsonUtils,
    QgsMeshLayer,
    NULL
)
from PyQt5.QtCore import QVariant

class LayerFactory:
    def __init__(self, api_client):
        self.api = api_client

    def add_wms_layer(self, layer_metadata):
        """Adds a WMS layer from GeoServer."""
        base_url = layer_metadata.get('serviceUrl', 'http://localhost/geoserver/wms')
        workspace = layer_metadata.get('workspace')
        layer_name = layer_metadata.get('layerName')
        full_layer_name = f"{workspace}:{layer_name}" if workspace else layer_name
        title = layer_metadata.get('name', layer_name)
        
        # QGIS WMS connection string
        uri = f"url={base_url}&layers={full_layer_name}&format=image/png&styles=&crs=EPSG:3857"
        
        rlayer = QgsRasterLayer(uri, title, "wms")
        if rlayer.isValid():
            QgsProject.instance().addMapLayer(rlayer)
            return rlayer
        else:
            QgsMessageLog.logMessage(f"WMS layer {layer_name} is invalid", "GeoInfoSystem", Qgis.Critical)
            return None

    def add_vector_collection(self, project_id, layer_type, title):
        """Fetches data and creates a vector layer for a specific collection (points, multilines, polygons)."""
        if layer_type == 'points':
            data = self.api.get_points(project_id)
            geom_type = "Point"
        elif layer_type == 'multilines':
            data = self.api.get_multilines(project_id)
            geom_type = "MultiLineString"
        elif layer_type == 'polygons':
            data = self.api.get_polygons(project_id)
            geom_type = "MultiPolygon"
        else:
            return None

        if not data:
            return None

        return self._create_layer_from_data(data, geom_type, title, project_id=project_id)

    def add_single_vector_object(self, obj_data):
        """Adds a single vector object to the map as a temporary layer."""
        title = obj_data.get('name', 'Object')
        geom_type = obj_data.get('type') # Point, MultiLineString, Polygon
        
        return self._create_layer_from_data([obj_data], geom_type, title)

    def _create_layer_from_data(self, data_list, geom_type, title, project_id=None):
        """Internal helper to create a layer from a list of DTOs."""
        # URI for memory layer
        uri = f"{geom_type}?crs=epsg:4326&field=id:string&field=name:string&field=description:string"
        vlayer = QgsVectorLayer(uri, title, "memory")
        
        if not vlayer.isValid():
            QgsMessageLog.logMessage(f"Failed to create memory layer for {title}", "GeoInfoSystem", Qgis.Critical)
            return None

        # Tag the layer with project ID for later synchronization
        if project_id:
            vlayer.setCustomProperty("geoinfo_project_id", str(project_id))

        pr = vlayer.dataProvider()
        vlayer.startEditing()
        
        for feature_data in data_list:
            fet = QgsFeature()
            geom_json = feature_data.get('geom')
            if geom_json:
                if isinstance(geom_json, dict):
                    fet.setGeometry(QgsJsonUtils.geometryFromGeoJson(json.dumps(geom_json)))
                else:
                    fet.setGeometry(QgsGeometry.fromWkt(geom_json))
            
            fet.setAttributes([
                str(feature_data.get('id')),
                feature_data.get('name', ''),
                feature_data.get('description', '')
            ])
            pr.addFeatures([fet])
            
        vlayer.commitChanges()
        vlayer.updateExtents()
        
        QgsProject.instance().addMapLayer(vlayer)
        return vlayer

    def add_terrain_layer(self, terrain_data):
        """Adds a terrain layer (Quantized Mesh) to QGIS."""
        raw_url = terrain_data.get('terrainUrl', '')
        title = terrain_data.get('title', 'Terrain Layer')
        
        # Sanitize URL: ensure it points to localhost/terrain correctly
        if 'terrain-worker' in raw_url or '8000' in raw_url:
            parts = raw_url.split('/')
            dataset_name = parts[-1] if parts[-1] else parts[-2]
            url = f"http://localhost/terrain/{dataset_name}"
        elif raw_url.startswith('http'):
            url = raw_url
        else:
            path = raw_url.lstrip('/')
            url = f"http://localhost/{path}" if path.startswith('terrain/') else f"http://localhost/terrain/{path}"
        
        # Remove any specific tile path if present (we need the root)
        import re
        match = re.search(r'(http://.*?/terrain/[^/]+)', url)
        if match:
            url = match.group(1)

        if not url.endswith('/'):
            url += '/'

        # List of provider/URI combinations to try
        test_configs = [
            ("mesh_cesium", f"type=cesium&url={url}layer.json"),
            ("mesh_cesium", f"type=cesium&url={url}"),
            ("mdal", f"{url}layer.json"),
            ("mdal", url),
        ]

        mlayer = None
        for provider, uri in test_configs:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Trying terrain [Provider: {provider}] URI: {uri}", "GeoInfoSystem", Qgis.Info)
            test_layer = QgsMeshLayer(uri, title, provider)
            if test_layer.isValid():
                mlayer = test_layer
                break

        if mlayer:
            QgsProject.instance().addMapLayer(mlayer)
            QgsMessageLog.logMessage(f"Terrain layer {title} added successfully", "GeoInfoSystem", Qgis.Success)
            return mlayer
        else:
            QgsMessageLog.logMessage(f"Terrain layer {title} is invalid after trying all URI formats. Please ensure 'mesh_cesium' provider is active.", "GeoInfoSystem", Qgis.Critical)
            return None

    def add_cog_layer(self, imagery_layer_data):
        """COG logic preserved but currently bypassed in UI as per request."""
        pass

    def export_feature_to_dto(self, feature, project_id, folder_id=None):
        """Converts a QgsFeature back to a DTO dictionary for the API."""
        geom = feature.geometry()
        # Convert QgsGeometry to GeoJSON dict string using asJson()
        geom_json_str = geom.asJson()
        geom_json = json.loads(geom_json_str) if geom_json_str else None
        
        dto = {
            "name": feature.attribute("name") or "New Object",
            "description": feature.attribute("description") or "",
            "projectId": project_id,
            "folderId": folder_id,
            "status": "COMPLETED", # Default status
            "geom": geom_json,
            "characteristics": {}
        }
        
        # If it's an existing feature, add its ID
        feat_id = feature.attribute("id")
        if feat_id and feat_id != NULL:
            dto["id"] = str(feat_id)
            
        return dto
