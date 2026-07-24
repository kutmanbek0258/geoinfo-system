from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QComboBox,
    QPushButton, QLabel, QMessageBox, QWidget
)

class SatelliteImportDialog(QDialog):
    def __init__(self, iface, api_client, job, parent=None):
        super().__init__(parent)
        self.iface = iface
        self.api = api_client
        self.job = job
        self.setWindowTitle(f"Импорт спутникового снимка: {job.get('name')}")
        self.setMinimumWidth(450)
        
        self.layout = QVBoxLayout(self)
        
        characs = job.get('characteristics') or {}
        metadata = characs.get('metadata') or {}
        dataType = characs.get('dataType', 'SENTINEL_2')
        self.is_sentinel = (dataType == 'SENTINEL_2')
        
        self.available_bands = metadata.get('availableBands', [])
        if not self.available_bands:
            # Fallbacks
            if self.is_sentinel:
                self.available_bands = ['B01', 'B02', 'B03', 'B04', 'B05', 'B06', 'B07', 'B08', 'B8A', 'B09', 'B10', 'B11', 'B12']
            else:
                self.available_bands = ['B1', 'B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8', 'B9', 'B10', 'B11']
                
        # Metadata Label
        meta_label = QLabel(
            f"<b>Извлеченные метаданные:</b><br/>"
            f"Проекция: {'Задана' if metadata.get('crs') else 'Не определена'}<br/>"
            f"Каналы: {', '.join(self.available_bands)}"
        )
        meta_label.setStyleSheet("padding: 8px; background-color: #eee; border-radius: 4px; margin-bottom: 10px;")
        self.layout.addWidget(meta_label)
        
        # Mode Selection
        self.layout.addWidget(QLabel("Режим импорта:"))
        self.mode_combo = QComboBox()
        self.mode_combo.addItem("Цветовой синтез (RGB Пресет)", "preset")
        self.mode_combo.addItem("Вегетационный/Водный индекс", "index")
        self.mode_combo.addItem("Пользовательский синтез каналов", "custom")
        self.mode_combo.currentIndexChanged.connect(self.on_mode_changed)
        self.layout.addWidget(self.mode_combo)
        
        # Container for dynamic elements
        self.dynamic_widget = QWidget()
        self.dynamic_layout = QVBoxLayout(self.dynamic_widget)
        self.dynamic_layout.setContentsMargins(0, 5, 0, 5)
        self.layout.addWidget(self.dynamic_widget)
        
        # Build options for modes
        # 1. Presets
        self.preset_combo = QComboBox()
        if self.is_sentinel:
            self.presets = {
                'Natural Color (B04, B03, B02)': ['B04', 'B03', 'B02'],
                'False Color Infrared (B08, B04, B03)': ['B08', 'B04', 'B03'],
                'Agriculture (B11, B08, B04)': ['B11', 'B08', 'B04'],
                'Shortwave Infrared (B12, B08, B04)': ['B12', 'B08', 'B04'],
                'Healthy Vegetation (B08, B11, B02)': ['B08', 'B11', 'B02']
            }
        else: # Landsat 8
            self.presets = {
                'Natural Color (B4, B3, B2)': ['B4', 'B3', 'B2'],
                'False Color Infrared (B5, B4, B3)': ['B5', 'B4', 'B3'],
                'Agriculture (B6, B5, B4)': ['B6', 'B5', 'B4'],
                'Shortwave Infrared (B7, B5, B4)': ['B7', 'B5', 'B4'],
                'Healthy Vegetation (B5, B6, B2)': ['B5', 'B6', 'B2']
            }
        for name in self.presets.keys():
            self.preset_combo.addItem(name)
            
        # 2. Indices
        self.index_combo = QComboBox()
        self.indices = ['NDVI', 'NDWI', 'NDMI', 'NBR', 'NDSI', 'NDBI', 'SAVI', 'EVI', 'GNDVI']
        for idx in self.indices:
            self.index_combo.addItem(idx)
            
        # 3. Custom Channels (R, G, B combo boxes)
        self.custom_widget = QWidget()
        custom_lay = QHBoxLayout(self.custom_widget)
        custom_lay.setContentsMargins(0, 0, 0, 0)
        
        self.r_combo = QComboBox()
        self.g_combo = QComboBox()
        self.b_combo = QComboBox()
        for band in self.available_bands:
            self.r_combo.addItem(band)
            self.g_combo.addItem(band)
            self.b_combo.addItem(band)
            
        # Default defaults for custom
        if len(self.available_bands) >= 3:
            if self.is_sentinel:
                self.r_combo.setCurrentText('B04')
                self.g_combo.setCurrentText('B03')
                self.b_combo.setCurrentText('B02')
            else:
                self.r_combo.setCurrentText('B4')
                self.g_combo.setCurrentText('B3')
                self.b_combo.setCurrentText('B2')
                
        custom_lay.addWidget(QLabel("R:"))
        custom_lay.addWidget(self.r_combo)
        custom_lay.addWidget(QLabel("G:"))
        custom_lay.addWidget(self.g_combo)
        custom_lay.addWidget(QLabel("B:"))
        custom_lay.addWidget(self.b_combo)
        
        # Trigger default state
        self.on_mode_changed()
        
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

    def on_mode_changed(self):
        # Clear dynamic layout
        while self.dynamic_layout.count():
            item = self.dynamic_layout.takeAt(0)
            widget = item.widget()
            if widget:
                widget.setParent(None)
                
        mode = self.mode_combo.currentData()
        if mode == 'preset':
            self.dynamic_layout.addWidget(QLabel("Спектральный пресет:"))
            self.dynamic_layout.addWidget(self.preset_combo)
            self.preset_combo.show()
        elif mode == 'index':
            self.dynamic_layout.addWidget(QLabel("Рассчитываемый индекс:"))
            self.dynamic_layout.addWidget(self.index_combo)
            self.index_combo.show()
        elif mode == 'custom':
            self.dynamic_layout.addWidget(QLabel("Выберите спектральные каналы:"))
            self.dynamic_layout.addWidget(self.custom_widget)
            self.custom_widget.show()

    def start_import(self):
        mode = self.mode_combo.currentData()
        task_type = 'SENTINEL_COG' if self.is_sentinel else 'LANDSAT_COG'
        
        channels = []
        index_type = None
        
        if mode == 'preset':
            preset_name = self.preset_combo.currentText()
            channels = self.presets.get(preset_name, [])
        elif mode == 'index':
            index_type = self.index_combo.currentText()
        else: # custom
            channels = [self.r_combo.currentText(), self.g_combo.currentText(), self.b_combo.currentText()]
            
        params = {
            "taskType": task_type,
            "channels": channels
        }
        if index_type:
            params["indexType"] = index_type
            
        self.import_btn.setEnabled(False)
        res = self.api.start_import(self.job.get('id'), params)
        self.import_btn.setEnabled(True)
        
        if res:
            QMessageBox.information(self, "Успех", "Импорт спутникового снимка успешно запущен.")
            self.accept()
        else:
            QMessageBox.critical(self, "Ошибка", "Не удалось запустить импорт.")
