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
    QgsRasterLayerElevationProperties,
    QgsHillshadeRenderer,
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
        """Adds a terrain layer to QGIS using COG (Raster Elevation)."""
        title = terrain_data.get('title', 'Terrain Layer')
        layer_id = terrain_data.get('id')
        
        if not layer_id:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Layer ID missing for {title}", "GeoInfoSystem", Qgis.Critical)
            return None

        # Request COG presigned URL
        cog_url = self.api.get_terrain_layer_presigned_url(layer_id)
        if not cog_url:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Could not get COG URL for terrain layer {title}", "GeoInfoSystem", Qgis.Critical)
            return None

        # Build full URL if relative
        if cog_url.startswith('/'):
            base_url = self.api.gateway_url
            # gateway_url might be http://localhost/api or http://sso.localhost/api
            # We must ensure we use the host that handles /terrain/cog/ (default_server)
            if 'sso.localhost' in base_url:
                base_url = base_url.replace('sso.localhost', 'localhost')
                
            if base_url.endswith('/api'):
                base_url = base_url[:-4]
            elif '/api/' in base_url:
                base_url = base_url.split('/api/')[0]
            elif ':9005' in base_url:
                base_url = base_url.split(':9005')[0]
                
            cog_url = base_url.rstrip('/') + cog_url
            
        # Wrap in /vsicurl/ - NO literal quotes here
        vsi_url = f"/vsicurl/{cog_url}"

        QgsMessageLog.logMessage(f"GeoInfoSystem: Adding {title} as Raster Elevation layer. URI: {vsi_url}", "GeoInfoSystem", Qgis.Info)
        
        # Add as raster layer using GDAL provider
        rlayer = QgsRasterLayer(vsi_url, title, "gdal")
        if rlayer.isValid():
            # Set Hillshade renderer to make it look "3D" in 2D map
            renderer = QgsHillshadeRenderer(rlayer.dataProvider(), 1, 45.0, 315.0)
            rlayer.setRenderer(renderer)
            
            QgsProject.instance().addMapLayer(rlayer)
            
            # Configure as Elevation layer for QGIS 3D
            elevation_props = rlayer.elevationProperties()
            elevation_props.setEnabled(True)
            
            # RepresentsElevationSurface tells QGIS this layer is a terrain source
            try:
                # Try setting the mode using the enum if available
                if hasattr(QgsRasterLayerElevationProperties, 'RepresentsElevationSurface'):
                    elevation_props.setMode(QgsRasterLayerElevationProperties.RepresentsElevationSurface)
                else:
                    elevation_props.setMode(Qgis.RasterElevationMode.RepresentsElevationSurface)
            except Exception as e:
                QgsMessageLog.logMessage(f"GeoInfoSystem: Could not set elevation mode: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            
            QgsMessageLog.logMessage(f"Terrain layer {title} (COG) added successfully. Open QGIS 3D View and select this layer as Terrain source.", "GeoInfoSystem", Qgis.Success)
            return rlayer
        else:
            error_msg = rlayer.dataProvider().error().message() if rlayer.dataProvider() else "Invalid layer"
            QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to add COG layer {title}. Error: {error_msg}", "GeoInfoSystem", Qgis.Critical)
            return None

    def add_cog_layer(self, imagery_layer_data):
        """Adds an imagery COG layer to QGIS via presigned URL."""
        title = imagery_layer_data.get('name', 'Imagery COG')
        layer_id = imagery_layer_data.get('id')
        
        if not layer_id:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Layer ID missing for {title}", "GeoInfoSystem", Qgis.Critical)
            return None

        # Request COG presigned URL for imagery layer
        cog_url = self.api.get_imagery_layer_presigned_url(layer_id)
        if not cog_url:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Could not get COG URL for imagery layer {title}", "GeoInfoSystem", Qgis.Critical)
            return None

        # Build full URL if relative
        if cog_url.startswith('/'):
            base_url = self.api.gateway_url
            # gateway_url might be http://localhost/api or http://sso.localhost/api
            # We must ensure we use the host that handles /imagery/cog/ (default_server)
            if 'sso.localhost' in base_url:
                base_url = base_url.replace('sso.localhost', 'localhost')
            
            if base_url.endswith('/api'):
                base_url = base_url[:-4]
            elif '/api/' in base_url:
                base_url = base_url.split('/api/')[0]
            elif ':9005' in base_url:
                base_url = base_url.split(':9005')[0]
                
            cog_url = base_url.rstrip('/') + cog_url
            
        # Wrap in /vsicurl/ - NO literal quotes here as they break GDAL connection (Code 0)
        vsi_url = f"/vsicurl/{cog_url}"

        QgsMessageLog.logMessage(f"GeoInfoSystem: Adding {title} as COG layer. URI: {vsi_url}", "GeoInfoSystem", Qgis.Info)
        
        # Add as raster layer using GDAL provider
        rlayer = QgsRasterLayer(vsi_url, f"{title} (COG)", "gdal")
        if rlayer.isValid():
            QgsProject.instance().addMapLayer(rlayer)
            QgsMessageLog.logMessage(f"Imagery COG layer {title} added successfully.", "GeoInfoSystem", Qgis.Success)
            return rlayer
        else:
            error_msg = rlayer.dataProvider().error().message() if rlayer.dataProvider() else "Invalid layer"
            QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to add Imagery COG layer {title}. Error: {error_msg}", "GeoInfoSystem", Qgis.Critical)
            return None

    def export_feature_to_dto(self, feature, project_id, folder_id=None, api_type=None):
        """Converts a QgsFeature back to a DTO dictionary for the API with high-stability normalization."""
        try:
            geom = feature.geometry()
            if not geom or geom.isNull() or geom.isEmpty():
                return None

            # Ensure geometry is valid to prevent crashes during asJson()
            if not geom.isGeosValid():
                QgsMessageLog.logMessage("GeoInfoSystem: Invalid geometry detected, attempting to fix...", "GeoInfoSystem", Qgis.Warning)
                geom = geom.makeValid()

            normalized_geom = geom
            
            # Universal Multi-type promotion and Topology Fix
            if not geom.isMultipart():
                QgsMessageLog.logMessage(f"GeoInfoSystem: Promoting single {api_type} to Multi-type...", "GeoInfoSystem", Qgis.Info)
                normalized_geom = geom.convertToMultiType()
            
            # CRITICAL FIX: Ensure all polygon rings are closed. 
            # Some QGIS providers/edits might produce unclosed rings that JTS (Backend) rejects.
            if api_type == 'polygons':
                abstract_geom = normalized_geom.constGet()
                if abstract_geom and abstract_geom.geometryType() == "MultiPolygon":
                    # We need to ensure each ring of each polygon is closed
                    # This is easier to do by letting QGIS re-validate or via explicit closure check
                    # Standard check: QGIS's makeValid() often fixes closure, but we'll do an explicit check if needed.
                    if not normalized_geom.isGeosValid():
                        normalized_geom = normalized_geom.makeValid()

            # Export to JSON
            QgsMessageLog.logMessage("GeoInfoSystem: Exporting geometry to JSON...", "GeoInfoSystem", Qgis.Info)
            geom_json_str = normalized_geom.asJson()
            geom_obj = json.loads(geom_json_str)
            
            # Extract geometry data from GeoJSON Feature if needed
            if isinstance(geom_obj, dict) and geom_obj.get('type') == 'Feature':
                geom_data = geom_obj.get('geometry')
            else:
                geom_data = geom_obj

            QgsMessageLog.logMessage("GeoInfoSystem: Building DTO...", "GeoInfoSystem", Qgis.Info)
            dto = {
                "name": feature.attribute("name") if "name" in feature.fields().names() else "New Object",
                "description": feature.attribute("description") if "description" in feature.fields().names() else "",
                "projectId": project_id,
                "folderId": folder_id,
                "status": "COMPLETED",
                "geom": geom_data,
                "characteristics": {}
            }
            
            if "id" in feature.fields().names():
                feat_id = feature.attribute("id")
                if feat_id and feat_id != NULL and str(feat_id).strip() != "":
                    dto["id"] = str(feat_id)
                
            return dto
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: CRITICAL ERROR in export_feature_to_dto: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return None
