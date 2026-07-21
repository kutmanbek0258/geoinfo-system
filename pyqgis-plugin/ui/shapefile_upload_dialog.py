import os
from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, 
    QComboBox, QPushButton, QLabel, 
    QLineEdit, QProgressBar, QMessageBox,
    QFileDialog, QApplication
)
from qgis.core import QgsProject, QgsMessageLog, Qgis

class ShapefileUploadDialog(QDialog):
    def __init__(self, iface, api_client, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.setWindowTitle("Upload Shapefile Archive (.zip) to GeoInfoSystem")
        self.setMinimumWidth(480)

        self.layout = QVBoxLayout(self)

        # Instructions
        self.layout.addWidget(QLabel("Upload a local Shapefile ZIP package to convert and import into project layers."))
        self.layout.addSpacing(10)

        # Target Project Selection (Required)
        self.layout.addWidget(QLabel("Target Project (Required):"))
        self.project_combo = QComboBox()
        self.populate_projects()
        self.layout.addWidget(self.project_combo)

        # File Selection
        self.layout.addWidget(QLabel("Select Shapefile ZIP Archive (.zip):"))
        file_layout = QHBoxLayout()
        self.file_edit = QLineEdit()
        self.file_edit.setPlaceholderText("Select .zip archive containing .shp, .dbf, .shx...")
        file_layout.addWidget(self.file_edit)

        self.browse_btn = QPushButton("Browse...")
        self.browse_btn.clicked.connect(self.browse_file)
        file_layout.addWidget(self.browse_btn)
        self.layout.addLayout(file_layout)

        # Display Name
        self.layout.addWidget(QLabel("Layer Name (Display in System):"))
        self.name_edit = QLineEdit()
        self.layout.addWidget(self.name_edit)

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
            for p in projects:
                self.project_combo.addItem(p.get('name', 'Unnamed Project'), p.get('id'))
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to fetch projects: {str(e)}", "GeoInfoSystem", Qgis.Warning)

    def browse_file(self):
        filename, _ = QFileDialog.getOpenFileName(self, "Select Shapefile ZIP Archive", "", "ZIP Archives (*.zip)")
        if filename:
            self.file_edit.setText(filename)
            if not self.name_edit.text().strip():
                base_name = os.path.splitext(os.path.basename(filename))[0]
                self.name_edit.setText(base_name)

    def start_upload(self):
        source_path = self.file_edit.text().strip()
        if not source_path or not os.path.exists(source_path):
            QMessageBox.warning(self, "Validation Failed", "Please select a valid .zip Shapefile archive.")
            return

        if not source_path.lower().endswith('.zip'):
            QMessageBox.warning(self, "Validation Failed", "Only .zip archives containing Shapefile components are supported.")
            return

        project_id = self.project_combo.currentData()
        if not project_id:
            QMessageBox.warning(self, "Validation Failed", "Vectors must be imported into a target project. Please select a project.")
            return

        name = self.name_edit.text().strip()
        if not name:
            QMessageBox.warning(self, "Validation Failed", "Please provide a name for the layer.")
            return

        # Prepare UI
        self.upload_btn.setEnabled(False)
        self.browse_btn.setEnabled(False)
        self.file_edit.setEnabled(False)
        self.project_combo.setEnabled(False)
        self.name_edit.setEnabled(False)
        self.progress_bar.setVisible(True)
        self.progress_label.setVisible(True)
        self.progress_label.setText("Requesting presigned URL...")
        self.progress_bar.setValue(0)
        QApplication.processEvents()

        try:
            filename = os.path.basename(source_path)
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

            success = self.api.upload_file(url, source_path, progress_cb)
            if not success:
                raise Exception("Transfer Error: File upload to storage failed.")

            self.progress_label.setText("Upload complete. Verifying Shapefile...")
            QApplication.processEvents()

            file_size = os.path.getsize(source_path)
            
            # 1. Verify
            job = self.api.verify_upload(name, object_key, file_size, "SHAPEFILE", project_id=project_id)
            if not job or not job.get('id'):
                raise Exception("API Error: Shapefile verification failed.")

            job_id = job.get('id')

            # 2. Trigger SHAPEFILE_TO_GEOJSON import
            confirm_res = self.api.start_import(job_id, {"taskType": "SHAPEFILE_TO_GEOJSON"})
            
            if confirm_res:
                QMessageBox.information(self, "Success", 
                    f"Shapefile archive '{name}' uploaded successfully!\n\n"
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
            self.browse_btn.setEnabled(True)
            self.file_edit.setEnabled(True)
            self.project_combo.setEnabled(True)
            self.name_edit.setEnabled(True)
            self.progress_bar.setVisible(False)
            self.progress_label.setVisible(False)
