from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QMessageBox
)

class TerrainImportDialog(QDialog):
    def __init__(self, iface, api_client, job, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.job = job
        self.setWindowTitle(f"Импорт 3D-Рельефа: {job.get('name')}")
        self.setMinimumWidth(400)
        
        self.layout = QVBoxLayout(self)
        
        # Metadata
        characs = job.get('characteristics') or {}
        metadata = characs.get('metadata') or {}
        
        meta_label = QLabel(
            f"<b>Свойства DEM растра:</b><br/>"
            f"Минимальная высота: {job.get('minHeight', 0.0)} м<br/>"
            f"Максимальная высота: {job.get('maxHeight', 0.0)} м<br/>"
            f"Файл: {metadata.get('filename', 'неизвестно')}"
        )
        meta_label.setStyleSheet("padding: 8px; background-color: #eee; border-radius: 4px; margin-bottom: 10px;")
        self.layout.addWidget(meta_label)
        
        # Description
        info_label = QLabel(
            "Будет запущен процесс триангуляции и конвертации высот в формат Cesium Quantized-Mesh. "
            "Полученный 3D-рельеф можно использовать на глобусе Cesium или в 3D View QGIS."
        )
        info_label.setWordWrap(True)
        info_label.setStyleSheet("color: #795548; background-color: #EFEBE9; padding: 8px; border-radius: 4px; margin-bottom: 15px;")
        self.layout.addWidget(info_label)
        
        # Buttons
        btns = QHBoxLayout()
        self.import_btn = QPushButton("Начать генерацию рельефа")
        self.import_btn.setStyleSheet("font-weight: bold; background-color: #795548; color: white;")
        self.import_btn.clicked.connect(self.start_import)
        btns.addWidget(self.import_btn)
        
        self.cancel_btn = QPushButton("Отмена")
        self.cancel_btn.clicked.connect(self.reject)
        btns.addWidget(self.cancel_btn)
        
        self.layout.addLayout(btns)

    def start_import(self):
        params = {
            "taskType": "TERRAIN_MESH"
        }
        
        self.import_btn.setEnabled(False)
        res = self.api.start_import(self.job.get('id'), params)
        self.import_btn.setEnabled(True)
        
        if res:
            QMessageBox.information(self, "Успех", "Генерация 3D-рельефа успешно запущена.")
            self.accept()
        else:
            QMessageBox.critical(self, "Ошибка", "Не удалось запустить генерацию рельефа.")
