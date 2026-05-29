import requests
from qgis.core import QgsMessageLog, Qgis

class GeoInfoAPIClient:
    def __init__(self, gateway_url, auth_service):
        self.gateway_url = gateway_url.rstrip('/')
        self.auth = auth_service

    def _get_headers(self):
        headers = {'Content-Type': 'application/json'}
        headers.update(self.auth.get_auth_header())
        return headers

    def _request(self, method, url, **kwargs):
        """Central request handler with automatic token refresh."""
        headers = kwargs.pop('headers', self._get_headers())
        
        try:
            response = requests.request(method, url, headers=headers, **kwargs)
            
            # If unauthorized, try to refresh token and retry ONCE
            if response.status_code == 401:
                QgsMessageLog.logMessage("GeoInfoSystem: Access token expired (401). Attempting refresh...", "GeoInfoSystem", Qgis.Info)
                if self.auth.refresh_token_func():
                    # Update headers with new token
                    headers.update(self.auth.get_auth_header())
                    QgsMessageLog.logMessage("GeoInfoSystem: Retrying request with new token...", "GeoInfoSystem", Qgis.Info)
                    response = requests.request(method, url, headers=headers, **kwargs)
            
            response.raise_for_status()
            return response
        except requests.exceptions.HTTPError as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: HTTP Error: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            raise e
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Request failed: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            raise e

    def get_projects(self, page=0, size=20):
        """Fetches the list of projects available to the user."""
        url = f"{self.gateway_url}/geodata/project/page-query"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            return response.json().get('content', [])
        except Exception:
            return []

    def get_folders(self, project_id):
        """Fetches the folder structure for a specific project."""
        url = f"{self.gateway_url}/geodata/folders/project/{project_id}"
        try:
            response = self._request('GET', url)
            return response.json()
        except Exception:
            return []

    def get_points(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/points/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            return response.json().get('content', [])
        except Exception:
            return []

    def get_multilines(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/multilines/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            return response.json().get('content', [])
        except Exception:
            return []

    def get_polygons(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/polygons/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            return response.json().get('content', [])
        except Exception:
            return []

    def get_imagery_layers(self, page=0, size=100):
        """Fetches available imagery layers."""
        url = f"{self.gateway_url}/geo-abstraction/imagery-layer/page-query"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            return response.json().get('content', [])
        except Exception:
            return []
            
    def get_terrain_layers(self, page=0, size=100):
        """Fetches available terrain layers."""
        url = f"{self.gateway_url}/geo-abstraction/layers"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            return response.json().get('content', [])
        except Exception:
            return []

    def get_terrain_layer_presigned_url(self, layer_id):
        """Requests a presigned URL for a specific terrain layer's COG."""
        url = f"{self.gateway_url}/geo-abstraction/layers/{layer_id}/presigned-url"
        try:
            response = self._request('GET', url)
            return response.json().get('url')
        except Exception:
            return None

    def get_imagery_layer_presigned_url(self, layer_id):
        """Requests a presigned URL for a specific imagery layer's COG."""
        url = f"{self.gateway_url}/geo-abstraction/imagery-layer/{layer_id}/presigned-url"
        try:
            response = self._request('GET', url)
            return response.json().get('url')
        except Exception:
            return None

    def get_presigned_url(self, filename):
        """Requests a presigned URL for direct S3 upload/download."""
        url = f"{self.gateway_url}/geo-abstraction/upload/presigned-url"
        params = {'filename': filename}
        try:
            response = self._request('GET', url, params=params)
            return response.json().get('url')
        except Exception:
            return None

    # --- Create / Update Methods ---

    def sync_feature(self, layer_type, feature_data, is_new=False):
        """Generic sync method to create or update a feature."""
        base_url = f"{self.gateway_url}/geodata/{layer_type}"
        
        # Determine method and final URL
        feat_id = feature_data.get('id')
        if is_new or not feat_id:
            url = base_url
            method = 'POST'
        else:
            url = f"{base_url}/{feat_id}"
            method = 'PUT'

        try:
            response = self._request(method, url, json=feature_data)
            return response.json()
        except Exception:
            return None
