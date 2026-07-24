from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QComboBox,
    QPushButton, QLabel, QMessageBox
)

class GeoTiffImportDialog(QDialog):
    def __init__(self, iface, api_client, job, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.job = job
        self.setWindowTitle(f"Импорт GeoTIFF: {job.get('name')}")
        self.setMinimumWidth(400)
        
        self.layout = QVBoxLayout(self)
        
        # Metadata
        characs = job.get('characteristics') or {}
        metadata = characs.get('metadata') or {}
        width = metadata.get('width', 0)
        height = metadata.get('height', 0)
        bands = metadata.get('bandsCount', 0)
        
        meta_label = QLabel(
            f"<b>Свойства растра:</b><br/>"
            f"Размер: {width} x {height} px<br/>"
            f"Каналов: {bands}<br/>"
            f"Файл: {metadata.get('filename', 'неизвестно')}"
        )
        meta_label.setStyleSheet("padding: 8px; background-color: #eee; border-radius: 4px; margin-bottom: 10px;")
        self.layout.addWidget(meta_label)
        
        # Render Mode Combobox
        self.layout.addWidget(QLabel("Режим визуализации:"))
        self.render_combo = QComboBox()
        self.render_combo.addItem("Аналитический (для числовых вычислений/DEM)", "analytic")
        self.render_combo.addItem("Web RGB (для визуализации/RGB ортофото)", "web_rgb")
        
        # Select web_rgb by default if bands >= 3
        if bands >= 3:
            self.render_combo.setCurrentIndex(1)
            
        self.layout.addWidget(self.render_combo)
        self.layout.addSpacing(15)
        
        # Buttons
        btns = QHBoxLayout()
        self.import_btn = QPushButton("Запустить импорт")
        self.import_btn.setStyleSheet("font-weight: bold;")
        self.import_btn.clicked.connect(self.start_import)
        btns.addWidget(self.import_btn)
        
        self.cancel_btn = QPushButton("Отмена")
        self.cancel_btn.clicked.connect(self.reject)
        btns.addWidget(self.cancel_btn)
        
        self.layout.addLayout(btns)

    def start_import(self):
        render_mode = self.render_combo.currentData()
        params = {
            "taskType": "RAW_GEOTIFF_OPTIMIZE",
            "renderMode": render_mode,
            "outputMode": render_mode
        }
        
        self.import_btn.setEnabled(False)
        res = self.api.start_import(self.job.get('id'), params)
        self.import_btn.setEnabled(True)
        
        if res:
            QMessageBox.information(self, "Успех", "Импорт успешно запущен в фоновом режиме.")
            self.accept()
        else:
            QMessageBox.critical(self, "Ошибка", "Не удалось запустить импорт.")
