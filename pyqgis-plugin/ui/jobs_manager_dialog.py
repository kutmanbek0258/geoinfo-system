import re
from qgis.PyQt.QtCore import Qt, QTimer
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout,
    QTableWidget, QTableWidgetItem, QPushButton,
    QLabel, QHeaderView, QMessageBox, QAbstractItemView
)
from qgis.PyQt.QtGui import QColor
from qgis.core import QgsMessageLog, Qgis

from .geotiff_import_dialog import GeoTiffImportDialog
from .shapefile_import_dialog import ShapefileImportDialog
from .satellite_import_dialog import SatelliteImportDialog
from .terrain_import_dialog import TerrainImportDialog
from .vector_upload_dialog import VectorUploadDialog
from .raster_upload_dialog import RasterUploadDialog


class JobsManagerDialog(QDialog):
    def __init__(self, iface, api_client, project_id=None, project_name=None, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.project_id = project_id
        
        title = "Задачи обработки (Все)"
        if project_id:
            title = f"Задачи проекта: {project_name or project_id[:8]}"
            
        self.setWindowTitle(title)
        self.setMinimumSize(650, 450)
        self.resize(700, 500)
        
        self.page = 0
        self.size = 10
        self.total_pages = 0
        self.jobs_list = []
        
        # UI Layout
        self.layout = QVBoxLayout(self)
        
        # Info Header
        self.info_label = QLabel("Список активных и завершенных задач импорта растров, векторов и рельефа.")
        self.info_label.setStyleSheet("font-size: 11px; color: #555;")
        self.layout.addWidget(self.info_label)
        
        # Table
        self.table = QTableWidget()
        self.table.setColumnCount(4)
        self.table.setHorizontalHeaderLabels(["Тип", "Название задачи", "Статус", "Действие"])
        self.table.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.table.setSelectionMode(QAbstractItemView.SingleSelection)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        
        # Header resize policies
        header = self.table.horizontalHeader()
        header.setSectionResizeMode(0, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.Stretch)
        header.setSectionResizeMode(2, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(3, QHeaderView.ResizeToContents)
        
        self.layout.addWidget(self.table)
        
        # Pagination & Control Layout
        controls_layout = QHBoxLayout()
        
        self.prev_btn = QPushButton("◀ Назад")
        self.prev_btn.clicked.connect(self.prev_page)
        controls_layout.addWidget(self.prev_btn)
        
        self.page_label = QLabel("Страница 1 из 1")
        self.page_label.setAlignment(Qt.AlignCenter)
        controls_layout.addWidget(self.page_label)
        
        self.next_btn = QPushButton("Вперед ▶")
        self.next_btn.clicked.connect(self.next_page)
        controls_layout.addWidget(self.next_btn)

        self.upload_vector_btn = QPushButton("Загрузить вектор")
        self.upload_vector_btn.clicked.connect(self.upload_vector)
        controls_layout.addWidget(self.upload_vector_btn)

        self.upload_raster_btn = QPushButton("Загрузить растр")
        self.upload_raster_btn.clicked.connect(self.upload_raster)
        controls_layout.addWidget(self.upload_raster_btn)
        
        self.refresh_btn = QPushButton("Обновить")
        self.refresh_btn.setStyleSheet("font-weight: bold;")
        self.refresh_btn.clicked.connect(self.refresh_jobs)
        controls_layout.addWidget(self.refresh_btn)
        
        self.close_btn = QPushButton("Закрыть")
        self.close_btn.clicked.connect(self.accept)
        controls_layout.addWidget(self.close_btn)
        
        self.layout.addLayout(controls_layout)
        
        # Setup Polling Timer (5 seconds)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.poll_jobs)
        self.timer.start(5000)
        
        # Load initial data
        self.refresh_jobs()

    def refresh_jobs(self):
        """Fetches jobs list from the API and repopulates the table."""
        res = self.api.get_jobs(project_id=self.project_id, page=self.page, size=self.size)
        self.jobs_list = res.get('content', [])
        self.total_pages = res.get('totalPages', 0)
        
        # Update pagination controls
        self.prev_btn.setEnabled(self.page > 0)
        self.next_btn.setEnabled(self.page < self.total_pages - 1)
        self.page_label.setText(f"Страница {self.page + 1} из {max(1, self.total_pages)}")
        
        self.table.setRowCount(0)
        
        for idx, job in enumerate(self.jobs_list):
            self.table.insertRow(idx)
            
            # 1. Type
            task_type = job.get('taskType')
            characs = job.get('characteristics') or {}
            data_type = characs.get('dataType')
            
            type_label, type_icon = self.get_task_icon_info(task_type, data_type)
            type_item = QTableWidgetItem(type_label)
            type_item.setToolTip(type_label)
            self.table.setItem(idx, 0, type_item)
            
            # 2. Name
            name_item = QTableWidgetItem(job.get('name', 'Unnamed task'))
            self.table.setItem(idx, 1, name_item)
            
            # 3. Status
            status = job.get('status', 'NEW').upper()
            status_item = QTableWidgetItem(self.get_status_label(status))
            
            # Color coding
            color = self.get_status_color(status)
            status_item.setForeground(color)
            
            # Show error tooltips
            err_msg = job.get('errorMessage')
            if err_msg:
                status_item.setToolTip(f"Ошибка: {err_msg}")
                
            self.table.setItem(idx, 2, status_item)
            
            # 4. Action Button
            if status == 'VERIFIED':
                btn = QPushButton("Импорт")
                btn.setStyleSheet("background-color: #00796B; color: white; font-weight: bold; max-height: 22px; padding: 2px 8px;")
                btn.clicked.connect(lambda checked, j=job: self.open_import_dialog(j))
                self.table.setCellWidget(idx, 3, btn)
            else:
                waiting_label = QLabel(self.get_status_action_text(status))
                waiting_label.setAlignment(Qt.AlignCenter)
                waiting_label.setStyleSheet("color: #777; font-size: 10px;")
                self.table.setCellWidget(idx, 3, waiting_label)

    def poll_jobs(self):
        """Polls jobs regularly if there are active tasks on the first page."""
        has_active_jobs = False
        for job in self.jobs_list:
            status = job.get('status', '').upper()
            if status in ['PROCESSING', 'QUEUED', 'VERIFYING']:
                has_active_jobs = True
                break
                
        if has_active_jobs or self.page == 0:
            self.refresh_jobs()

    def prev_page(self):
        if self.page > 0:
            self.page -= 1
            self.refresh_jobs()

    def next_page(self):
        if self.page < self.total_pages - 1:
            self.page += 1
            self.refresh_jobs()

    def upload_vector(self):
        dialog = VectorUploadDialog(self.iface, self.api, project_id=self.project_id, parent=self)
        if dialog.exec_():
            self.refresh_jobs()

    def upload_raster(self):
        dialog = RasterUploadDialog(self.iface, self.api, project_id=self.project_id, parent=self)
        if dialog.exec_():
            self.refresh_jobs()

    def open_import_dialog(self, job):
        """Opens the specific import confirmation dialog based on job's dataType."""
        characs = job.get('characteristics') or {}
        data_type = characs.get('dataType', '').upper()
        
        QgsMessageLog.logMessage(f"GeoInfoSystem: Opening import dialog for job {job.get('id')} ({data_type})", "GeoInfoSystem", Qgis.Info)
        
        dialog = None
        if data_type == 'GEOTIFF':
            dialog = GeoTiffImportDialog(self.iface, self.api, job, self)
        elif data_type == 'SHAPEFILE':
            dialog = ShapefileImportDialog(self.iface, self.api, job, self)
        elif data_type in ['SENTINEL_2', 'LANDSAT_8']:
            dialog = SatelliteImportDialog(self.iface, self.api, job, self)
        elif data_type == 'TERRAIN':
            dialog = TerrainImportDialog(self.iface, self.api, job, self)
        else:
            # Fallback confirmation for other models (3D Tiles, CityGML, etc.)
            confirm = QMessageBox.question(
                self, "Импорт задачи",
                f"Запустить импорт для '{job.get('name')}' (Тип: {data_type})?",
                QMessageBox.Yes | QMessageBox.No
            )
            if confirm == QMessageBox.Yes:
                self.trigger_generic_import(job)
                return
                
        if dialog and dialog.exec_():
            self.refresh_jobs()

    def trigger_generic_import(self, job):
        """Triggers import for jobs without specialized UI dialogs."""
        characs = job.get('characteristics') or {}
        data_type = characs.get('dataType', '').upper()
        
        task_map = {
            '3D_TILES': '3D_TILES',
            'CITYGML': 'CITYGML',
            'NETCDF': 'NETCDF_COG'
        }
        
        task_type = task_map.get(data_type, 'RAW_GEOTIFF_OPTIMIZE')
        
        res = self.api.start_import(job.get('id'), {"taskType": task_type})
        if res:
            QMessageBox.information(self, "Успех", "Задача импорта успешно запущена.")
            self.refresh_jobs()
        else:
            QMessageBox.critical(self, "Ошибка", "Не удалось запустить задачу импорта.")

    def closeEvent(self, event):
        """Stop polling timer when the dialog is closed."""
        self.timer.stop()
        super().closeEvent(event)

    # --- UI Helper mappings ---

    def get_task_icon_info(self, task_type, data_type):
        if not task_type or task_type == 'VERIFY_FILE':
            if data_type == 'SHAPEFILE':
                return "Shapefile", "polygon"
            elif data_type == 'SENTINEL_2':
                return "Sentinel-2", "satellite"
            elif data_type == 'LANDSAT_8':
                return "Landsat-8", "satellite"
            elif data_type == 'GEOTIFF':
                return "GeoTIFF", "raster"
            elif data_type == 'TERRAIN':
                return "Terrain DEM", "terrain"
            elif data_type == 'NETCDF':
                return "NetCDF", "netcdf"
            return "Верификация", "file"
            
        if task_type == 'SHAPEFILE_TO_GEOJSON':
            return "Shapefile", "polygon"
        elif task_type == 'SENTINEL_COG':
            return "Sentinel-2", "satellite"
        elif task_type == 'LANDSAT_COG':
            return "Landsat-8", "satellite"
        elif task_type == 'RAW_GEOTIFF_OPTIMIZE':
            return "GeoTIFF", "raster"
        elif task_type == 'TERRAIN_MESH':
            return "Terrain DEM", "terrain"
        elif task_type == 'NETCDF_COG':
            return "NetCDF", "netcdf"
        return task_type, "cog"

    def get_status_label(self, status):
        status_map = {
            'READY': 'Готов',
            'FAILED': 'Ошибка ❌',
            'PROCESSING': 'Обработка ⚙️',
            'VERIFYING': 'Проверка 🔍',
            'VERIFIED': 'Проверен',
            'QUEUED': 'В очереди ⏳'
        }
        return status_map.get(status, status)

    def get_status_color(self, status):
        # Return QColor based on job state
        colors = {
            'READY': QColor(56, 142, 60),      # Dark Green
            'FAILED': QColor(211, 47, 47),      # Red
            'PROCESSING': QColor(25, 118, 210),  # Blue
            'VERIFYING': QColor(230, 81, 0),    # Orange
            'VERIFIED': QColor(0, 121, 107),    # Teal
            'QUEUED': QColor(97, 97, 97)        # Grey
        }
        return colors.get(status, QColor(0, 0, 0))

    def get_status_action_text(self, status):
        actions = {
            'READY': 'Готово',
            'FAILED': 'Ошибка',
            'PROCESSING': 'Обработка',
            'VERIFYING': 'Проверка',
            'QUEUED': 'В очереди'
        }
        return actions.get(status, '-')
