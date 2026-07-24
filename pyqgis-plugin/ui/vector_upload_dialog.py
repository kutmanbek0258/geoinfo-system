import os
import glob
import zipfile
import tempfile
from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, 
    QComboBox, QPushButton, QLabel, 
    QLineEdit, QProgressBar, QMessageBox,
    QApplication
)
from qgis.core import QgsProject, QgsVectorLayer, QgsMessageLog, Qgis

class VectorUploadDialog(QDialog):
    def __init__(self, iface, api_client, project_id=None, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.project_id = project_id
        
        self.setWindowTitle("Upload Vector Layer to GeoInfoSystem")
        self.setMinimumWidth(450)

        self.layout = QVBoxLayout(self)

        # Instructions
        self.layout.addWidget(QLabel("Upload a local Shapefile vector layer to convert and import into project layers."))
        self.layout.addSpacing(10)

        # Target Project Selection (Optional)
        self.layout.addWidget(QLabel("Target Project (Optional):"))
        self.project_combo = QComboBox()
        self.project_combo.addItem("--- Global / No Project ---", None)
        self.populate_projects()
        self.layout.addWidget(self.project_combo)

        # Layer Selection
        self.layout.addWidget(QLabel("Select Vector Layer from Map (Shapefile):"))
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
        self.upload_btn = QPushButton("Start Upload & Import")
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
            selected_index = 0
            for idx, p in enumerate(projects):
                p_id = p.get('id')
                self.project_combo.addItem(p.get('name', 'Unnamed Project'), p_id)
                if self.project_id and str(p_id) == str(self.project_id):
                    # index + 1 due to Global Item at index 0
                    selected_index = idx + 1
                    
            if self.project_id:
                self.project_combo.setCurrentIndex(selected_index)
                self.project_combo.setEnabled(False)
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to fetch projects: {str(e)}", "GeoInfoSystem", Qgis.Warning)

    def populate_layers(self):
        layers = QgsProject.instance().mapLayers().values()
        vector_layers_found = False
        for layer in layers:
            if isinstance(layer, QgsVectorLayer):
                source = layer.source()
                # Verify that it is a local shapefile
                if source and source.lower().endswith('.shp') and os.path.exists(source):
                    self.layer_combo.addItem(layer.name(), layer.id())
                    vector_layers_found = True
        
        if not vector_layers_found:
            self.upload_btn.setEnabled(False)
            no_layers_lbl = QLabel("<font color='red'>No local Shapefile (.shp) layers found in map.</font>")
            self.layout.addWidget(no_layers_lbl)

    def on_layer_changed(self):
        self.name_edit.setText(self.layer_combo.currentText())

    def start_upload(self):
        layer_id = self.layer_combo.currentData()
        if not layer_id:
            return

        layer = QgsProject.instance().mapLayer(layer_id)
        if not layer:
            return

        shp_path = layer.source()
        if not os.path.exists(shp_path):
            QMessageBox.critical(self, "Error", f"Source Shapefile not found: {shp_path}")
            return

        name = self.name_edit.text().strip()
        if not name:
            QMessageBox.warning(self, "Validation Failed", "Please provide a name for the layer.")
            return

        # Prepare UI
        self.upload_btn.setEnabled(False)
        self.layer_combo.setEnabled(False)
        self.name_edit.setEnabled(False)
        self.progress_bar.setVisible(True)
        self.progress_label.setVisible(True)
        self.progress_label.setText("Archiving Shapefile files...")
        self.progress_bar.setValue(0)
        QApplication.processEvents()

        temp_zip_path = None
        try:
            # 1. Collect all Shapefile companion files (.shp, .shx, .dbf, .prj, etc.)
            dir_name = os.path.dirname(shp_path)
            base_name = os.path.splitext(os.path.basename(shp_path))[0]
            shapefile_files = glob.glob(os.path.join(dir_name, f"{base_name}.*"))
            
            if not shapefile_files:
                raise Exception("No shapefile component files found.")

            # 2. Package them into a temporary ZIP file
            temp_zip = tempfile.NamedTemporaryFile(suffix='.zip', delete=False)
            temp_zip_path = temp_zip.name
            temp_zip.close() # Close so we can write to it
            
            QgsMessageLog.logMessage(f"GeoInfoSystem: Zipping shapefile files to {temp_zip_path}", "GeoInfoSystem", Qgis.Info)
            with zipfile.ZipFile(temp_zip_path, 'w', zipfile.ZIP_DEFLATED) as zip_file:
                for file in shapefile_files:
                    zip_file.write(file, os.path.basename(file))

            # 3. Request presigned URL for upload
            zip_filename = f"{base_name}.zip"
            upload_info = self.api.get_upload_url_info(zip_filename)
            if not upload_info:
                raise Exception("API Error: Could not obtain presigned upload URL.")

            url = upload_info.get('url')
            object_key = upload_info.get('objectKey')

            self.progress_label.setText("Uploading archive...")
            
            def progress_cb(current, total):
                percent = int((current / total) * 100)
                self.progress_bar.setValue(percent)
                self.progress_label.setText(f"Uploading: {percent}% ({current // 1024} / {total // 1024} KB)")
                QApplication.processEvents()

            # 4. Upload temporary ZIP
            success = self.api.upload_file(url, temp_zip_path, progress_cb)
            if not success:
                raise Exception("Transfer Error: File upload to storage failed.")

            self.progress_label.setText("Upload complete. Verifying Shapefile...")
            QApplication.processEvents()

            file_size = os.path.getsize(temp_zip_path)
            project_id = self.project_combo.currentData()
            
            # 5. Trigger verify upload task
            job = self.api.verify_upload(name, object_key, file_size, "SHAPEFILE", project_id=project_id)
            if not job or not job.get('id'):
                raise Exception("API Error: Shapefile verification failed.")

            job_id = job.get('id')

            # 6. Trigger final import task
            confirm_res = self.api.start_import(job_id, {"taskType": "SHAPEFILE_TO_GEOJSON"})
            
            if confirm_res:
                QMessageBox.information(self, "Success", 
                    f"Shapefile '{name}' archived and uploaded successfully!\n\n"
                    "Background worker will convert features to GeoJSON WGS84 and "
                    "ingest them into project layers. Refresh project tree after a few moments.")
                self.accept()
            else:
                raise Exception("API Error: Shapefile import task creation failed.")

        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Shapefile upload failed: {str(e)}", "GeoInfoSystem", Qgis.Critical)
            QMessageBox.critical(self, "Upload Failed", f"An error occurred during Shapefile upload:\n{str(e)}")
            
            # Reset UI
            self.upload_btn.setEnabled(True)
            self.layer_combo.setEnabled(True)
            self.name_edit.setEnabled(True)
            self.progress_bar.setVisible(False)
            self.progress_label.setVisible(False)
            
        finally:
            # Clean up the temporary zip file
            if temp_zip_path and os.path.exists(temp_zip_path):
                try:
                    os.remove(temp_zip_path)
                    QgsMessageLog.logMessage(f"GeoInfoSystem: Cleaned up temporary archive {temp_zip_path}", "GeoInfoSystem", Qgis.Info)
                except Exception as ex:
                    QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to remove temporary archive: {str(ex)}", "GeoInfoSystem", Qgis.Warning)
