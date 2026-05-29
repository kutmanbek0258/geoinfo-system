import hashlib
import base64
import secrets
import webbrowser
import requests
from urllib.parse import urlencode, urlparse, parse_qs
from http.server import HTTPServer, BaseHTTPRequestHandler
from qgis.core import QgsMessageLog, Qgis

class OAuth2CallbackHandler(BaseHTTPRequestHandler):
    """Temporary HTTP server to handle the OAuth2 callback."""
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html; charset=utf-8')
        self.end_headers()
        
        query = urlparse(self.path).query
        params = parse_qs(query)
        
        if 'code' in params:
            self.server.authorization_code = params['code'][0]
            message = "<h1>Authorization Successful</h1><p>You can close this window and return to QGIS.</p>"
        else:
            message = "<h1>Authorization Failed</h1><p>Check your credentials and try again.</p>"
            
        self.wfile.write(message.encode('utf-8'))

    def log_message(self, format, *args):
        # Suppress server logs in console
        return

class AuthService:
    def __init__(self, base_url="http://sso.localhost", client_id="test-client", redirect_port=5678):
        self.base_url = base_url.rstrip('/')
        self.client_id = client_id
        self.redirect_port = redirect_port
        self.redirect_uri = f"http://localhost:{redirect_port}/code"
        # Basic auth header for test-client:test-client as seen in frontend
        self.auth_header_value = 'Basic dGVzdC1jbGllbnQ6dGVzdC1jbGllbnQ='
        self.access_token = None
        self.refresh_token = None
        self.id_token = None
        self.scopes = 'SSO.USER_PROFILE_INFO SSO.USER_AVATAR SSO.USER_IDENTIFICATION SSO.USER_AUTHORITIES'

    def _generate_pkce(self):
        """Generates Code Verifier and Code Challenge for PKCE."""
        verifier = secrets.token_urlsafe(64)
        sha256 = hashlib.sha256(verifier.encode('ascii')).digest()
        challenge = base64.urlsafe_b64encode(sha256).decode('ascii').replace('=', '')
        return verifier, challenge

    def login(self):
        """Starts the browser-based OAuth2 login flow."""
        verifier, challenge = self._generate_pkce()
        
        params = {
            'response_type': 'code',
            'client_id': self.client_id,
            'redirect_uri': self.redirect_uri,
            'code_challenge': challenge,
            'code_challenge_method': 'S256',
            'scope': self.scopes
        }
        
        # Explicitly build the URL to avoid formatting issues
        query_string = urlencode(params)
        auth_url = f"{self.base_url}/oauth2/authorize?{query_string}"
        
        # Start local server to listen for callback
        server = HTTPServer(('localhost', self.redirect_port), OAuth2CallbackHandler)
        server.authorization_code = None
        
        QgsMessageLog.logMessage(f"Opening browser for authentication: {auth_url}", "GeoInfoSystem", Qgis.Info)
        webbrowser.open(auth_url)
        
        # Wait for code
        server.handle_request()
        
        if server.authorization_code:
            return self._exchange_code(server.authorization_code, verifier)
        return False

    def _exchange_code(self, code, verifier):
        """Exchanges Authorization Code for Tokens."""
        token_url = f"{self.base_url}/oauth2/token"
        data = {
            'grant_type': 'authorization_code',
            'code': code,
            'redirect_uri': self.redirect_uri,
            'client_id': self.client_id,
            'code_verifier': verifier
        }
        
        headers = {
            'Content-type': 'application/x-www-form-urlencoded',
            'Authorization': self.auth_header_value
        }
        
        try:
            # Using data=data for form-urlencoded
            response = requests.post(token_url, data=data, headers=headers)
            response.raise_for_status()
            tokens = response.json()
            
            self.access_token = tokens.get('access_token')
            self.refresh_token = tokens.get('refresh_token')
            
            QgsMessageLog.logMessage("Successfully authenticated with GeoInfoSystem (SSO)", "GeoInfoSystem", Qgis.Success)
            return True
        except Exception as e:
            QgsMessageLog.logMessage(f"Token exchange failed: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            return False

    def refresh_token_func(self):
        """Refreshes the access token using the refresh token."""
        if not self.refresh_token:
            QgsMessageLog.logMessage("No refresh token available.", "GeoInfoSystem", Qgis.Warning)
            return False

        token_url = f"{self.base_url}/oauth2/token"
        data = {
            'grant_type': 'refresh_token',
            'refresh_token': self.refresh_token,
            'client_id': self.client_id
        }
        
        headers = {
            'Content-type': 'application/x-www-form-urlencoded',
            'Authorization': self.auth_header_value
        }
        
        try:
            QgsMessageLog.logMessage("Attempting to refresh access token...", "GeoInfoSystem", Qgis.Info)
            response = requests.post(token_url, data=data, headers=headers)
            response.raise_for_status()
            tokens = response.json()
            
            self.access_token = tokens.get('access_token')
            # If the server provides a new refresh token, update it
            if tokens.get('refresh_token'):
                self.refresh_token = tokens.get('refresh_token')
            
            QgsMessageLog.logMessage("Access token refreshed successfully.", "GeoInfoSystem", Qgis.Success)
            return True
        except Exception as e:
            QgsMessageLog.logMessage(f"Token refresh failed: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            # If refresh fails, we might need to clear tokens to force re-login
            self.access_token = None
            self.refresh_token = None
            return False

    def get_auth_header(self):
        if self.access_token:
            return {'Authorization': f'Bearer {self.access_token}'}
        return {}
