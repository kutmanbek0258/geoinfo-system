import requests
from qgis.core import QgsMessageLog, Qgis
from .config import DEFAULT_SSO_HOST, DEFAULT_DOMAIN

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

    def get_project_layers(self, project_id):
        """Fetches logical layers for a specific project."""
        url = f"{self.gateway_url}/geodata/layers/project/{project_id}"
        try:
            response = self._request('GET', url)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception:
            return []

    def get_folders(self, project_id):
        """Fetches the folder structure for a specific project."""
        url = f"{self.gateway_url}/geodata/folders/project/{project_id}"
        try:
            response = self._request('GET', url)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception:
            return []


    def get_points(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/points/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception:
            return []

    def get_multilines(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/multilines/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception:
            return []

    def get_polygons(self, project_id, page=0, size=1000):
        url = f"{self.gateway_url}/geodata/polygons/by-project-id/{project_id}"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception:
            return []


    def get_imagery_layers(self, page=0, size=100):
        """Fetches available global raster layers."""
        url = f"{self.gateway_url}/geodata/raster-layers"
        try:
            response = self._request('GET', url)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Error fetching raster layers: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            return []
            
    def get_terrain_layers(self, page=0, size=100):
        """Fetches available terrain layers."""
        url = f"{self.gateway_url}/geodata/terrain-layers"
        params = {'page': page, 'size': size}
        try:
            response = self._request('GET', url, params=params)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Error fetching terrain layers: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            return []

    def get_project_rasters_by_layer(self, layer_id):
        """Fetches project rasters for a specific layer."""
        url = f"{self.gateway_url}/geodata/project-rasters/layer/{layer_id}"
        try:
            response = self._request('GET', url)
            res_data = response.json()
            if isinstance(res_data, list):
                return res_data
            elif isinstance(res_data, dict):
                return res_data.get('content', [])
            return []
        except Exception:
            return []


    def get_terrain_layer_presigned_url(self, layer_id):
        """Requests a presigned URL for a specific terrain layer's COG."""
        url = f"{self.gateway_url}/geodata/terrain-layers/{layer_id}/presigned-url"
        try:
            response = self._request('GET', url)
            return response.json().get('url')
        except Exception:
            return None

    def get_imagery_layer_presigned_url(self, layer_id):
        """Requests a presigned URL for a specific raster layer's COG."""
        url = f"{self.gateway_url}/geodata/raster-layers/{layer_id}/presigned-url"
        try:
            response = self._request('GET', url)
            return response.json().get('url')
        except Exception:
            return None

    def get_project_raster_presigned_url(self, raster_id):
        """Requests a presigned URL for a specific project raster's COG."""
        url = f"{self.gateway_url}/geodata/project-rasters/{raster_id}/presigned-url"
        try:
            response = self._request('GET', url)
            return response.json().get('url')
        except Exception:
            return None



    def get_upload_url_info(self, filename):
        """Requests a presigned URL and object key for direct S3 upload."""
        url = f"{self.gateway_url}/geo-abstraction/upload/presigned-url"
        params = {'filename': filename}
        try:
            response = self._request('GET', url, params=params)
            return response.json() # returns {'url': '...', 'objectKey': '...'}
        except Exception:
            return None

    def upload_file(self, url, file_path, progress_callback=None):
        """Uploads a file using PUT method with progress tracking and io.BufferedReader."""
        import io
        import os

        # Build full URL if relative

        if url.startswith('/'):
            base_url = self.gateway_url
            if DEFAULT_SSO_HOST in base_url:
                base_url = base_url.replace(DEFAULT_SSO_HOST, DEFAULT_DOMAIN)
            
            # Extract protocol and host
            if base_url.endswith('/api'):
                base_url = base_url[:-4]
            elif '/api/' in base_url:
                base_url = base_url.split('/api/')[0]
            
            url = base_url.rstrip('/') + url
            QgsMessageLog.logMessage(f"GeoInfoSystem: Reconstructed absolute upload URL: {url}", "GeoInfoSystem", Qgis.Info)


        file_size = os.path.getsize(file_path)
        
        class ProgressFileWrapper:
            def __init__(self, fileobj, callback, total):
                self.fileobj = fileobj
                self.callback = callback
                self.total = total
                self.current = 0

            def read(self, size=-1):
                data = self.fileobj.read(size)
                if data:
                    self.current += len(data)
                    if self.callback:
                        self.callback(self.current, self.total)
                return data

            def __len__(self):
                return self.total

        try:
            # Using io.open with buffering for high performance
            with io.open(file_path, 'rb', buffering=1024*1024) as f:
                wrapped_file = ProgressFileWrapper(f, progress_callback, file_size)
                # Direct PUT to MinIO (Presigned URL)
                # Note: Content-Type is OMITTED because it must match the signature 
                # from MinioClient.getPresignedObjectUrl which currently doesn't include it.
                response = requests.put(url, data=wrapped_file)
                response.raise_for_status()
                return True
        except Exception as e:
            error_details = str(e)
            if hasattr(e, 'response') and e.response is not None:
                error_details += f" (Status: {e.response.status_code}, Body: {e.response.text})"
            QgsMessageLog.logMessage(f"GeoInfoSystem: Upload failed: {error_details}", "GeoInfoSystem", Qgis.Critical)
            return False

    def get_point(self, id):
        url = f"{self.gateway_url}/geodata/points/{id}"
        try:
            response = self._request('GET', url)
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: get_point failed: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            return None

    def get_multiline(self, id):
        url = f"{self.gateway_url}/geodata/multilines/{id}"
        try:
            response = self._request('GET', url)
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: get_multiline failed: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            return None

    def get_polygon(self, id):
        url = f"{self.gateway_url}/geodata/polygons/{id}"
        try:
            response = self._request('GET', url)
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: get_polygon failed: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            return None

    def get_project_hierarchy(self, project_id):
        """Fetches the complete, ordered tree hierarchy of layers, folders, and features for a project."""
        url = f"{self.gateway_url}/geodata/project/{project_id}/hierarchy"
        try:
            response = self._request('GET', url)
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: get_project_hierarchy failed: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            return None

    def get_jobs(self, project_id=None, page=0, size=20):
        """Fetches the list of background jobs/tasks from geo-abstraction service."""
        url = f"{self.gateway_url}/geo-abstraction/jobs"
        params = {'page': page, 'size': size}
        if project_id:
            params['projectId'] = project_id
        try:
            response = self._request('GET', url, params=params)
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: get_jobs failed: {str(e)}", "GeoInfoSystem", Qgis.Warning)
            return {'content': [], 'totalPages': 0}

    def verify_upload(self, name, object_key, file_size, data_type, project_id=None):
        """Registers uploaded file and triggers VERIFY_FILE task."""
        url = f"{self.gateway_url}/geo-abstraction/jobs/verify-upload"
        params = {
            'name': name,
            'objectKey': object_key,
            'fileSize': file_size,
            'dataType': data_type
        }
        if project_id:
            params['projectId'] = project_id
            
        try:
            response = self._request('POST', url, params=params)
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: verify_upload failed: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return None

    def start_import(self, job_id, params):
        """Starts final import processing for a verified job."""
        url = f"{self.gateway_url}/geo-abstraction/jobs/{job_id}/import"
        try:
            response = self._request('POST', url, json=params)
            return response.json()
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: start_import failed: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return None

    def confirm_raster_upload(self, name, object_key, file_size, task_type="RAW_GEOTIFF_OPTIMIZE", projectId=None):
        """2-Step compatibility helper: verifier -> import."""
        job = self.verify_upload(name, object_key, file_size, "GEOTIFF", project_id=projectId)
        if not job:
            return None
        job_id = job.get('id')
        if not job_id:
            return None
        return self.start_import(job_id, {"taskType": task_type})


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
