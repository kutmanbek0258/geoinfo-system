from qgis.PyQt.QtWidgets import QDialog, QVBoxLayout, QComboBox, QLineEdit, QPushButton, QLabel

class CreateObjectDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Create New GeoObject")
        self.layout = QVBoxLayout()
        
        self.layout.addWidget(QLabel("Select Type:"))
        self.type_combo = QComboBox()
        self.type_combo.addItems(["Folder", "Point", "Line", "Polygon"])
        self.layout.addWidget(self.type_combo)
        
        self.layout.addWidget(QLabel("Name:"))
        self.name_input = QLineEdit()
        self.layout.addWidget(self.name_input)
        
        btn = QPushButton("Create")
        btn.clicked.connect(self.accept)
        self.layout.addWidget(btn)
        
        self.setLayout(self.layout)

    def get_data(self):
        return self.type_combo.currentText(), self.name_input.text()
