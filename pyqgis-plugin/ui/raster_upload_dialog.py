import os
from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, 
    QComboBox, QPushButton, QLabel, 
    QLineEdit, QProgressBar, QMessageBox,
    QApplication
)
from qgis.core import QgsProject, QgsRasterLayer, QgsMessageLog, Qgis

class RasterUploadDialog(QDialog):
    def __init__(self, iface, api_client, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.setWindowTitle("Upload Raster to GeoInfoSystem")
        self.setMinimumWidth(450)

        self.layout = QVBoxLayout(self)

        # Instructions
        self.layout.addWidget(QLabel("Upload a local GeoTIFF layer as an Imagery Layer (COG)."))
        self.layout.addSpacing(10)

        # Project Selection
        self.layout.addWidget(QLabel("Associate with Project (Optional):"))
        self.project_combo = QComboBox()
        self.project_combo.addItem("--- Global / No Project ---", None)
        self.populate_projects()
        self.layout.addWidget(self.project_combo)

        # Layer Selection
        self.layout.addWidget(QLabel("Select Raster Layer from Map:"))
        self.layer_combo = QComboBox()
        self.populate_layers()
        self.layout.addWidget(self.layer_combo)

        # Display Name
        self.layout.addWidget(QLabel("Layer Name (Display in System):"))
        self.name_edit = QLineEdit()
        self.layout.addWidget(self.name_edit)
        
        # Connect combo change to update name
        self.layer_combo.currentIndexChanged.connect(self.on_layer_changed)
        self.on_layer_changed()

        self.layout.addSpacing(10)

        # Progress Section
        self.progress_label = QLabel("Ready")
        self.progress_label.setVisible(False)
        self.layout.addWidget(self.progress_label)
        
        self.progress_bar = QProgressBar()
        self.progress_bar.setVisible(False)
        self.layout.addWidget(self.progress_bar)

        self.layout.addStretch()

        # Buttons
        btns = QHBoxLayout()
        self.upload_btn = QPushButton("Start Upload")
        self.upload_btn.setStyleSheet("font-weight: bold; padding: 5px;")
        self.upload_btn.clicked.connect(self.start_upload)
        btns.addWidget(self.upload_btn)
        
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        btns.addWidget(self.cancel_btn)
        
        self.layout.addLayout(btns)

    def populate_projects(self):
        try:
            projects = self.api.get_projects()
            for p in projects:
                self.project_combo.addItem(p.get('name', 'Unnamed Project'), p.get('id'))
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to fetch projects for upload dialog: {str(e)}", "GeoInfoSystem", Qgis.Warning)

    def populate_layers(self):
        layers = QgsProject.instance().mapLayers().values()
        raster_layers_found = False
        for layer in layers:
            if isinstance(layer, QgsRasterLayer):
                # Try to filter only file-based layers
                source = layer.source()
                if os.path.exists(source):
                    self.layer_combo.addItem(layer.name(), layer.id())
                    raster_layers_found = True
        
        if not raster_layers_found:
            self.upload_btn.setEnabled(False)
            self.layout.addWidget(QLabel("<font color='red'>No local raster layers found in project.</font>"))

    def on_layer_changed(self):
        self.name_edit.setText(self.layer_combo.currentText())

    def start_upload(self):
        layer_id = self.layer_combo.currentData()
        if not layer_id:
            return

        layer = QgsProject.instance().mapLayer(layer_id)
        if not layer:
            return

        source_path = layer.source()
        if not os.path.exists(source_path):
            QMessageBox.critical(self, "Error", f"Source file not found: {source_path}")
            return

        # Explicit GeoTIFF validation
        if not (source_path.lower().endswith('.tif') or source_path.lower().endswith('.tiff')):
            QMessageBox.warning(self, "Validation Failed", "The selected layer source is not a GeoTIFF file. Only .tif and .tiff are supported.")
            return

        name = self.name_edit.text().strip()
        if not name:
            QMessageBox.warning(self, "Validation Failed", "Please provide a name for the imagery layer.")
            return

        # Prepare UI
        self.upload_btn.setEnabled(False)
        self.layer_combo.setEnabled(False)
        self.name_edit.setEnabled(False)
        self.progress_bar.setVisible(True)
        self.progress_label.setVisible(True)
        self.progress_label.setText("Requesting presigned URL...")
        self.progress_bar.setValue(0)
        QApplication.processEvents()

        try:
            filename = os.path.basename(source_path)
            project_id = self.project_combo.currentData()
            
            upload_info = self.api.get_upload_url_info(filename)
            if not upload_info:
                raise Exception("API Error: Could not obtain presigned upload URL.")

            url = upload_info.get('url')
            object_key = upload_info.get('objectKey')

            self.progress_label.setText(f"Uploading {filename}...")
            
            def progress_cb(current, total):
                percent = int((current / total) * 100)
                self.progress_bar.setValue(percent)
                self.progress_label.setText(f"Uploading: {percent}% ({current // 1024} / {total // 1024} KB)")
                QApplication.processEvents()

            # Upload using the new API client method (io.BufferedReader inside)
            success = self.api.upload_file(url, source_path, progress_cb)
            if not success:
                raise Exception("Transfer Error: File upload to storage failed.")

            self.progress_label.setText("Upload complete. Registering layer in system...")
            QApplication.processEvents()

            file_size = os.path.getsize(source_path)
            # Confirm to start RAW_GEOTIFF_OPTIMIZE task
            confirm_res = self.api.confirm_raster_upload(name, object_key, file_size, projectId=project_id)
            
            if confirm_res:
                QMessageBox.information(self, "Success", 
                    f"Raster '{name}' uploaded successfully!\n\n"
                    "The system has started the optimization process (COG) and "
                    "will publish the layer to GeoServer shortly. "
                    "Check the 'Imagery Layers' folder after a few moments.")
                self.accept()
            else:
                raise Exception("API Error: Upload confirmation failed.")

        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Raster upload failed: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            QMessageBox.critical(self, "Upload Failed", f"An error occurred during upload:\n{str(e)}")
            
            # Reset UI
            self.upload_btn.setEnabled(True)
            self.layer_combo.setEnabled(True)
            self.name_edit.setEnabled(True)
            self.progress_bar.setVisible(False)
            self.progress_label.setVisible(False)
