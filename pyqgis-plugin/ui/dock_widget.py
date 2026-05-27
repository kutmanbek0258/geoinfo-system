from qgis.PyQt.QtCore import Qt, pyqtSignal
from qgis.PyQt.QtWidgets import (
    QDockWidget, 
    QVBoxLayout, 
    QWidget, 
    QTreeView, 
    QPushButton, 
    QLabel
)
from qgis.PyQt.QtGui import QStandardItemModel, QStandardItem
from qgis.core import QgsMessageLog, Qgis, NULL, QgsProject, QgsVectorLayer

class GeoInfoDockWidget(QDockWidget):
    def __init__(self, iface, api_client, layer_factory, parent=None):
        super(GeoInfoDockWidget, self).__init__(parent)
        QgsMessageLog.logMessage("GeoInfoSystem: DockWidget Initializing...", "GeoInfoSystem", Qgis.Info)
        self.iface = iface
        self.api = api_client
        self.layer_factory = layer_factory
        
        self.setObjectName("GeoInfoSystemExplorer")
        self.setWindowTitle("GeoInfoSystem Explorer")
        self.setAllowedAreas(Qt.LeftDockWidgetArea | Qt.RightDockWidgetArea)

        self.content_widget = QWidget()
        self.layout = QVBoxLayout(self.content_widget)

        self.label = QLabel("Projects and Layers")
        self.layout.addWidget(self.label)

        # Tree View for projects and layers
        self.tree_view = QTreeView()
        self.model = QStandardItemModel()
        self.model.setHorizontalHeaderLabels(['Name', 'Type'])
        self.tree_view.setModel(self.model)
        self.layout.addWidget(self.tree_view)

        # Buttons layout
        self.btn_layout = QVBoxLayout()
        
        # Refresh button
        self.refresh_btn = QPushButton("Refresh")
        self.refresh_btn.clicked.connect(self.refresh_data)
        self.btn_layout.addWidget(self.refresh_btn)

        # Add button
        self.add_btn = QPushButton("Add to Map")
        self.add_btn.clicked.connect(self.add_selected_layer)
        self.btn_layout.addWidget(self.add_btn)

        # Sync button
        self.sync_btn = QPushButton("Synchronize Changes")
        self.sync_btn.setStyleSheet("background-color: #e1f5fe; font-weight: bold;")
        self.sync_btn.clicked.connect(self.synchronize_changes)
        self.btn_layout.addWidget(self.sync_btn)

        self.layout.addLayout(self.btn_layout)

        self.setWidget(self.content_widget)

    def synchronize_changes(self):
        """Finds all vector layers added by the plugin and syncs changes to the server."""
        QgsMessageLog.logMessage("GeoInfoSystem: Starting synchronization...", "GeoInfoSystem", Qgis.Info)
        
        layers = QgsProject.instance().mapLayers().values()
        sync_count = 0
        
        # Determine fallback project ID from the tree if nothing else found
        fallback_project_id = None
        for i in range(self.model.rowCount()):
            item = self.model.item(i)
            if item and item.data(Qt.UserRole) and item.data(Qt.UserRole).get('id'):
                fallback_project_id = item.data(Qt.UserRole).get('id')
                break

        for layer in layers:
            if not isinstance(layer, QgsVectorLayer):
                continue
            
            # Check for our marker or required fields
            project_id = layer.customProperty("geoinfo_project_id") or fallback_project_id
            
            # A layer is syncable if it has project_id and at least the 'name' field
            fields = [f.name().lower() for f in layer.fields()]
            has_name = 'name' in fields
            
            QgsMessageLog.logMessage(f"GeoInfoSystem: Inspecting layer '{layer.name()}' [Project: {project_id}, HasName: {has_name}]", "GeoInfoSystem", Qgis.Info)
            
            if not project_id or not has_name:
                QgsMessageLog.logMessage(f"GeoInfoSystem: Skipped layer '{layer.name()}' - missing project ID or 'name' field.", "GeoInfoSystem", Qgis.Warning)
                continue
            
            # Determine geometry type for API
            geom_type = layer.geometryType()
            api_type = None
            if geom_type == 0: api_type = "points"
            elif geom_type == 1: api_type = "multilines"
            elif geom_type == 2: api_type = "polygons"
            
            if not api_type: continue

            # Sync all features
            features = list(layer.getFeatures())
            QgsMessageLog.logMessage(f"GeoInfoSystem: Layer '{layer.name()}' has {len(features)} features. Syncing...", "GeoInfoSystem", Qgis.Info)
            
            for feature in features:
                # We identify new features by lack of valid UUID in 'id' field
                feat_id = feature.attribute("id")
                is_new = (feat_id == NULL or not str(feat_id).strip() or len(str(feat_id)) < 10)
                
                dto = self.layer_factory.export_feature_to_dto(feature, project_id)
                
                # Send to API
                result = self.api.sync_feature(api_type, dto, is_new=is_new)
                if result:
                    sync_count += 1
                    if is_new:
                        new_id = str(result.get('id'))
                        layer.startEditing()
                        layer.changeAttributeValue(feature.id(), layer.fields().indexOf("id"), new_id)
                        layer.commitChanges()
                        QgsMessageLog.logMessage(f"GeoInfoSystem: Created new {api_type} with ID {new_id}", "GeoInfoSystem", Qgis.Success)

        QgsMessageLog.logMessage(f"GeoInfoSystem: Synchronization finished. {sync_count} features processed.", "GeoInfoSystem", Qgis.Success)
        self.iface.messageBar().pushMessage("GeoInfoSystem", f"Synchronized {sync_count} features.", level=Qgis.Success)

    def refresh_data(self):
        """Loads projects and layers from API and builds a hierarchical tree."""
        self.model.removeRows(0, self.model.rowCount())
        
        projects = self.api.get_projects()
        imagery_layers = self.api.get_imagery_layers()
        terrain_layers = self.api.get_terrain_layers()

        for p in projects:
            project_id = p.get('id')
            p_item = QStandardItem(p.get('name', 'Unnamed Project'))
            p_item.setData(p, Qt.UserRole)
            p_item.setEditable(False)
            self.model.appendRow([p_item, QStandardItem("Project")])
            
            # 1. Vectors Folder (Hierarchical)
            vectors_root = QStandardItem("Vectors")
            vectors_root.setData({'type': 'group'}, Qt.UserRole)
            p_item.appendRow([vectors_root, QStandardItem("Folder")])
            
            self._build_vector_hierarchy(project_id, vectors_root)

            # 2. WMS Layers Folder
            wms_root = QStandardItem("WMS Layers")
            wms_root.setData({'type': 'group'}, Qt.UserRole)
            p_item.appendRow([wms_root, QStandardItem("Folder")])
            
            for l in imagery_layers:
                # In a real scenario, we might want to filter imagery by project if possible
                l_item = QStandardItem(l.get('name', 'Unnamed Imagery'))
                l_item.setData(l, Qt.UserRole)
                l_item.setEditable(False)
                wms_root.appendRow([l_item, QStandardItem("WMS")])

            # 3. Terrain Layers Folder
            terrain_root = QStandardItem("Terrain Layers")
            terrain_root.setData({'type': 'group'}, Qt.UserRole)
            p_item.appendRow([terrain_root, QStandardItem("Folder")])
            
            for t in terrain_layers:
                t_item = QStandardItem(t.get('title', 'Unnamed Terrain'))
                t_item.setData(t, Qt.UserRole)
                t_item.setEditable(False)
                terrain_root.appendRow([t_item, QStandardItem("Terrain")])

    def _build_vector_hierarchy(self, project_id, parent_item):
        """Builds the recursive folder and object structure for vectors."""
        folders = self.api.get_folders(project_id)
        points = self.api.get_points(project_id)
        multilines = self.api.get_multilines(project_id)
        polygons = self.api.get_polygons(project_id)

        all_objects = []
        for p in points: all_objects.append({**p, 'type': 'Point'})
        for m in multilines: all_objects.append({**m, 'type': 'MultiLineString'})
        for poly in polygons: all_objects.append({**poly, 'type': 'Polygon'})

        # Map for quick folder access
        folder_items = {}
        
        # 1. Create all folder items first
        for f in folders:
            f_item = QStandardItem(f.get('name'))
            f_item.setData({**f, 'type': 'folder'}, Qt.UserRole)
            folder_items[f.get('id')] = f_item

        # 2. Arrange folders into hierarchy
        for f in folders:
            f_id = f.get('id')
            p_id = f.get('parentId')
            f_item = folder_items[f_id]
            
            if p_id and p_id in folder_items:
                folder_items[p_id].appendRow([f_item, QStandardItem("Folder")])
            else:
                parent_item.appendRow([f_item, QStandardItem("Folder")])

        # 3. Add objects to folders or root
        for obj in all_objects:
            obj_item = QStandardItem(obj.get('name'))
            obj_item.setData(obj, Qt.UserRole)
            
            f_id = obj.get('folderId')
            if f_id and f_id in folder_items:
                folder_items[f_id].appendRow([obj_item, QStandardItem(obj['type'])])
            else:
                parent_item.appendRow([obj_item, QStandardItem(obj['type'])])

    def add_selected_layer(self):
        """Adds the selected item from the tree to the QGIS map."""
        index = self.tree_view.currentIndex()
        if not index.isValid():
            return
            
        item = self.model.itemFromIndex(index)
        data = item.data(Qt.UserRole)
        
        if not data or data.get('type') in ['group', 'folder']:
            return

        # Case 1: Imagery Layer (WMS)
        if 'layerName' in data:
            self.layer_factory.add_wms_layer(data)
        
        # Case 2: Terrain Layer
        elif 'terrainUrl' in data:
            self.layer_factory.add_terrain_layer(data)
        
        # Case 3: Vector Object
        elif 'geom' in data:
            self.layer_factory.add_single_vector_object(data)

    def add_selected_folder(self):
        """Future: Add all objects within a folder/project."""
        pass
