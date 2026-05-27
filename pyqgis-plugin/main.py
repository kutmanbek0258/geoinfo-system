import os
from qgis.PyQt.QtCore import Qt, QSettings, QTranslator, QCoreApplication
from qgis.PyQt.QtGui import QIcon
from qgis.PyQt.QtWidgets import QAction
from qgis.core import QgsMessageLog, Qgis

from .core.auth import AuthService
from .core.api_client import GeoInfoAPIClient
from .core.layer_factory import LayerFactory
from .ui.dock_widget import GeoInfoDockWidget
from qgis.core import QgsMessageLog, Qgis, QgsProviderRegistry

class GeoInfoSystemConnector:
    def __init__(self, iface):
        self.iface = iface
        self.plugin_dir = os.path.dirname(__file__)
        
        # Diagnostic: Log available providers
        providers = QgsProviderRegistry.instance().providerList()
        QgsMessageLog.logMessage(f"GeoInfoSystem: Available providers: {', '.join(providers)}", "GeoInfoSystem", Qgis.Info)
        
        # Initialize Services
        # Default settings aligned with frontend and sso.localhost requirements
        self.gateway_url = "http://localhost/api"
        self.auth_url = "http://sso.localhost"
        self.client_id = "test-client"
        
        self.auth = AuthService(self.auth_url, self.client_id)
        self.api = GeoInfoAPIClient(self.gateway_url, self.auth)
        self.layer_factory = LayerFactory(self.api)
        
        self.dock_widget = None

    def initGui(self):
        """Initializes the plugin UI."""
        icon_path = os.path.join(self.plugin_dir, 'icons', 'logo.png')
        self.action = QAction(
            QIcon(icon_path),
            "GeoInfoSystem Connector",
            self.iface.mainWindow()
        )
        self.action.triggered.connect(self.run)
        
        self.iface.addPluginToMenu("&GeoInfoSystem", self.action)
        self.iface.addToolBarIcon(self.action)

    def unload(self):
        """Removes the plugin from QGIS."""
        self.iface.removePluginMenu("&GeoInfoSystem", self.action)
        self.iface.removeToolBarIcon(self.action)
        if self.dock_widget:
            self.iface.removeDockWidget(self.dock_widget)

    def run(self):
        """Main entry point when user clicks the icon."""
        QgsMessageLog.logMessage("GeoInfoSystem: Running plugin action", "GeoInfoSystem", Qgis.Info)
        
        # Check authentication
        if not self.auth.access_token:
            QgsMessageLog.logMessage("GeoInfoSystem: Access token missing, starting login flow", "GeoInfoSystem", Qgis.Info)
            success = self.auth.login()
            if not success:
                QgsMessageLog.logMessage("GeoInfoSystem: Login failed or cancelled", "GeoInfoSystem", Qgis.Warning)
                return

        # Initialize DockWidget if it doesn't exist
        if not self.dock_widget:
            QgsMessageLog.logMessage("GeoInfoSystem: Creating DockWidget", "GeoInfoSystem", Qgis.Info)
            self.dock_widget = GeoInfoDockWidget(self.iface, self.api, self.layer_factory)
            self.iface.addDockWidget(Qt.RightDockWidgetArea, self.dock_widget)
        
        # Show the panel
        self.dock_widget.setVisible(True)
        self.dock_widget.show()
        QgsMessageLog.logMessage("GeoInfoSystem: Panel shown", "GeoInfoSystem", Qgis.Success)
