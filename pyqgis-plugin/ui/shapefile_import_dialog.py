from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QMessageBox
)

class ShapefileImportDialog(QDialog):
    def __init__(self, iface, api_client, job, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.job = job
        self.setWindowTitle(f"Импорт Shapefile: {job.get('name')}")
        self.setMinimumWidth(400)
        
        self.layout = QVBoxLayout(self)
        
        # Metadata
        characs = job.get('characteristics') or {}
        metadata = characs.get('metadata') or {}
        total_features = metadata.get('totalFeatures', 0)
        layers = ", ".join(metadata.get('layerNames', []) or ['основной слой'])
        geom_types = ", ".join(metadata.get('geomTypes', []) or ['Векторные геометрии'])
        
        meta_label = QLabel(
            f"<b>Результаты верификации пакета:</b><br/>"
            f"Всего объектов: <b>{total_features}</b><br/>"
            f"Слои в файле: {layers}<br/>"
            f"Типы геометрий: {geom_types}<br/>"
            f"Формат: {metadata.get('format', 'SHAPEFILE_ZIP')}"
        )
        meta_label.setStyleSheet("padding: 8px; background-color: #eee; border-radius: 4px; margin-bottom: 10px;")
        self.layout.addWidget(meta_label)
        
        # Warning/Information
        info_label = QLabel(
            "Все геометрии будут автоматически трансформированы в систему координат "
            "<b>WGS 84 (EPSG:4326)</b>, а атрибуты DBF импортированы в слой проекта."
        )
        info_label.setWordWrap(True)
        info_label.setStyleSheet("color: #00796B; background-color: #E0F2F1; padding: 8px; border-radius: 4px; margin-bottom: 15px;")
        self.layout.addWidget(info_label)
        
        # Buttons
        btns = QHBoxLayout()
        self.import_btn = QPushButton("Подтвердить и импортировать")
        self.import_btn.setStyleSheet("font-weight: bold; background-color: #E64A19; color: white;")
        self.import_btn.clicked.connect(self.start_import)
        btns.addWidget(self.import_btn)
        
        self.cancel_btn = QPushButton("Отмена")
        self.cancel_btn.clicked.connect(self.reject)
        btns.addWidget(self.cancel_btn)
        
        self.layout.addLayout(btns)

    def start_import(self):
        params = {
            "taskType": "SHAPEFILE_TO_GEOJSON"
        }
        
        self.import_btn.setEnabled(False)
        res = self.api.start_import(self.job.get('id'), params)
        self.import_btn.setEnabled(True)
        
        if res:
            QMessageBox.information(self, "Успех", "Импорт векторов успешно запущен в фоновом режиме.")
            self.accept()
        else:
            QMessageBox.critical(self, "Ошибка", "Не удалось запустить импорт.")
