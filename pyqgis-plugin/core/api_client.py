import requests
from qgis.core import QgsMessageLog, Qgis

class GeoInfoAPIClient:
    def __init__(self, gateway_url, auth_service):
        self.gateway_url = gateway_url.rstrip('/')
        self.auth = auth_service

    def _get_headers(self):
        headers = {'Content-Type': 'application/json'}
        headers.update(self.auth.get_auth_header())
        QgsMessageLog.logMessage(f"GeoInfoSystem: Headers prepared: {headers}", "GeoInfoSystem", Qgis.Info)
        return headers

    def get_projects(self, page=0, size=20):
        """Fetches the list of projects available to the user."""
        url = f"{self.gateway_url}/geodata/project/page-query"
        params = {'page': page, 'size': size}
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url} | Params: {params}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers(), params=params)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('content', [])
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to fetch projects: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return []

    def get_folders(self, project_id):
        """Fetches the folder structure for a specific project."""
        url = f"{self.gateway_url}/geodata/folders/project/{project_id}"
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers())
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to fetch folders for project {project_id}: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return []

    def get_points(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/points/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url} | Params: {params}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers(), params=params)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('content', [])
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to fetch points: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return []

    def get_multilines(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/multilines/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url} | Params: {params}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers(), params=params)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('content', [])
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to fetch multilines: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return []

    def get_polygons(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/polygons/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url} | Params: {params}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers(), params=params)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('content', [])
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to fetch polygons: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return []

    def get_imagery_layers(self, page=0, size=100):
        """Fetches available imagery layers."""
        url = f"{self.gateway_url}/geo-abstraction/imagery-layer/page-query"
        params = {'page': page, 'size': size}
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url} | Params: {params}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers(), params=params)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('content', [])
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to fetch imagery layers: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return []
            
    def get_terrain_layers(self, page=0, size=100):
        """Fetches available terrain layers."""
        url = f"{self.gateway_url}/geo-abstraction/layers"
        params = {'page': page, 'size': size}
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url} | Params: {params}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers(), params=params)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('content', [])
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to fetch terrain layers: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return []

    def get_terrain_layer_presigned_url(self, layer_id):
        """Requests a presigned URL for a specific terrain layer's COG."""
        url = f"{self.gateway_url}/geo-abstraction/layers/{layer_id}/presigned-url"
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers())
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('url')
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to get presigned URL for terrain layer {layer_id}: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return None

    def get_presigned_url(self, filename):
        """Requests a presigned URL for direct S3 upload/download."""
        url = f"{self.gateway_url}/geo-abstraction/upload/presigned-url"
        params = {'filename': filename}
        QgsMessageLog.logMessage(f"GeoInfoSystem: GET {url} | Params: {params}", "GeoInfoSystem", Qgis.Info)
        try:
            response = requests.get(url, headers=self._get_headers(), params=params)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json().get('url')
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to get presigned URL: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return None

    # --- Create / Update Methods ---

    def sync_feature(self, layer_type, feature_data, is_new=False):
        """Generic sync method to create or update a feature."""
        # layer_type: points, multilines, polygons
        # is_new: if True, use POST, otherwise PUT
        
        base_url = f"{self.gateway_url}/geodata/{layer_type}"
        url = base_url if is_new else f"{base_url}/{feature_data.get('id')}"
        method = requests.post if is_new else requests.put

        QgsMessageLog.logMessage(f"GeoInfoSystem: {'POST' if is_new else 'PUT'} {url}", "GeoInfoSystem", Qgis.Info)
        try:
            response = method(url, headers=self._get_headers(), json=feature_data)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Sync Response [{response.status_code}]: {response.text}", "GeoInfoSystem", Qgis.Info)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"Failed to sync feature: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return None
