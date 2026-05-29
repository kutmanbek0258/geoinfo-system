from qgis.PyQt.QtCore import Qt, pyqtSignal, QVariant
from qgis.PyQt.QtWidgets import (
    QDockWidget, 
    QVBoxLayout, 
    QHBoxLayout,
    QWidget, 
    QTreeView, 
    QPushButton, 
    QLabel,
    QMessageBox
)
from qgis.PyQt.QtGui import QStandardItemModel, QStandardItem
from qgis.core import QgsMessageLog, Qgis, NULL, QgsProject, QgsVectorLayer, QgsField
# from .create_dialog import CreateObjectDialog

class GeoInfoDockWidget(QDockWidget):
    def __init__(self, iface, api_client, layer_factory, parent=None):
        super(GeoInfoDockWidget, self).__init__(parent)
        self.iface = iface
        self.api = api_client
        self.layer_factory = layer_factory
        
        self.setObjectName("GeoInfoSystemExplorer")
        self.setWindowTitle("GeoInfoSystem Explorer")
        self.setAllowedAreas(Qt.LeftDockWidgetArea | Qt.RightDockWidgetArea)

        self.content_widget = QWidget()
        self.layout = QVBoxLayout(self.content_widget)

        # Header with + button
        header_layout = QHBoxLayout()
        header_layout.addWidget(QLabel("Projects and Layers"))
        self.add_new_btn = QPushButton("+")
        self.add_new_btn.setFixedWidth(30)
        self.add_new_btn.clicked.connect(self.show_create_dialog)
        header_layout.addWidget(self.add_new_btn)
        self.layout.addLayout(header_layout)

        # Tree View for projects and layers
        self.tree_view = QTreeView()
        self.model = QStandardItemModel()
        self.model.setHorizontalHeaderLabels(['Name', 'Type'])
        self.tree_view.setModel(self.model)
        self.layout.addWidget(self.tree_view)

        # Buttons layout
        self.btn_layout = QVBoxLayout()
        
        main_btns = QHBoxLayout()
        self.refresh_btn = QPushButton("Refresh")
        self.refresh_btn.clicked.connect(self.refresh_data)
        main_btns.addWidget(self.refresh_btn)

        self.add_btn = QPushButton("Add to Map")
        self.add_btn.clicked.connect(self.add_selected_layer)
        main_btns.addWidget(self.add_btn)

        self.sync_btn = QPushButton("Sync")
        self.sync_btn.clicked.connect(self.synchronize_changes)
        main_btns.addWidget(self.sync_btn)
        
        self.btn_layout.addLayout(main_btns)

        # Add to Project Button
        self.bind_btn = QPushButton("Add to Project")
        self.bind_btn.setToolTip("Bind selected QGIS vector layers to the selected folder/project in the tree.")
        self.bind_btn.clicked.connect(self.bind_layers_to_project)
        self.btn_layout.addWidget(self.bind_btn)

        self.layout.addLayout(self.btn_layout)
        self.setWidget(self.content_widget)

    def get_or_create_external_id_field(self, layer):
        """Finds or creates a string field capable of holding a UUID."""
        fields = layer.fields()
        
        # 1. Check for dedicated 'ginf_id' (GeoInfo ID)
        idx = fields.indexOf("ginf_id")
        if idx != -1:
            return idx, "ginf_id"
            
        # 2. Check if existing 'id' is a string
        idx = fields.indexOf("id")
        if idx != -1 and fields[idx].type() == QVariant.String:
            return idx, "id"
            
        # 3. Create 'ginf_id' if possible
        if not layer.isEditable() and not layer.startEditing():
            return -1, None
            
        # Shapefile limit is 10 chars, 'ginf_id' is 7.
        new_field = QgsField("ginf_id", QVariant.String, "text", 36)
        if layer.dataProvider().addAttributes([new_field]):
            layer.updateFields()
            return layer.fields().indexOf("ginf_id"), "ginf_id"
            
        return -1, None

    def show_create_dialog(self):
        index = self.tree_view.currentIndex()
        if not index.isValid():
            QgsMessageLog.logMessage("Please select a project or folder", "GeoInfoSystem", Qgis.Warning)
            return
            
        QgsMessageLog.logMessage("Create Object dialog is currently unavailable (missing create_dialog.py).", "GeoInfoSystem", Qgis.Warning)
        return

    def bind_layers_to_project(self):
        """Binds selected QGIS vector layers to the selected folder/project in the tree."""
        index = self.tree_view.currentIndex()
        if not index.isValid():
            QMessageBox.warning(self, "GeoInfoSystem", "Please select a target folder or 'Vectors' node in the tree.")
            return

        item = self.model.itemFromIndex(index)
        data = item.data(Qt.UserRole) or {}
        
        project_id = None
        folder_id = None

        # Determine target project and folder
        if data.get('type') == 'folder':
            folder_id = data.get('id')
            project_id = data.get('projectId')
        elif data.get('type') == 'group' and item.text() == 'Vectors':
            # Need to find project ID from parent
            parent = item.parent()
            if parent:
                project_id = parent.data(Qt.UserRole).get('id')
        
        if not project_id:
            QMessageBox.warning(self, "GeoInfoSystem", "Could not determine target project. Please select a folder or 'Vectors' node.")
            return

        selected_layers = self.iface.layerTreeView().selectedLayers()
        vector_layers = [l for l in selected_layers if isinstance(l, QgsVectorLayer)]

        if not vector_layers:
            QMessageBox.warning(self, "GeoInfoSystem", "No vector layers selected in the QGIS Layer Panel.")
            return

        confirm = QMessageBox.question(
            self, "Confirm Import",
            f"Import {len(vector_layers)} layers into project '{project_id}'?",
            QMessageBox.Yes | QMessageBox.No
        )
        if confirm != QMessageBox.Yes:
            return

        total_sync_count = 0
        for layer in vector_layers:
            # Determine API type
            geom_type = layer.geometryType()
            api_type = None
            if geom_type == 0: api_type = "points"
            elif geom_type == 1: api_type = "multilines"
            elif geom_type == 2: api_type = "polygons"
            
            if not api_type:
                continue

            # Tag layer with project and folder IDs for persistence
            layer.setCustomProperty("geoinfo_project_id", str(project_id))
            if folder_id:
                layer.setCustomProperty("geoinfo_folder_id", str(folder_id))
            else:
                layer.setCustomProperty("geoinfo_folder_id", "") # Clear if root
            
            # Use batch editing for stability
            layer.startEditing()
            try:
                # Ensure we have a suitable ID field
                id_field_idx, id_field_name = self.get_or_create_external_id_field(layer)
                
                features = list(layer.getFeatures())
                
                for feature in features:
                    dto = self.layer_factory.export_feature_to_dto(feature, project_id, folder_id, api_type=api_type, source_layer=layer)
                    if not dto: continue
                    
                    # Check if it already has a UUID in our chosen ID field
                    feat_id = feature.attribute(id_field_name) if id_field_idx != -1 else None
                    
                    # Helper to check if string is a valid UUID
                    import re
                    is_uuid = feat_id and feat_id != NULL and bool(re.match(r'^[0-9a-f]{8}-', str(feat_id), re.I))
                    is_new = not is_uuid

                    result = self.api.sync_feature(api_type, dto, is_new=is_new)
                    if result:
                        total_sync_count += 1
                        if is_new and id_field_idx != -1:
                            new_id = str(result.get('id'))
                            layer.changeAttributeValue(feature.id(), id_field_idx, new_id)
                
                layer.commitChanges()
                QgsMessageLog.logMessage(f"GeoInfoSystem: Layer '{layer.name()}' synced successfully.", "GeoInfoSystem", Qgis.Success)
            except Exception as e:
                layer.rollBack()
                QgsMessageLog.logMessage(f"GeoInfoSystem: Error syncing layer '{layer.name()}': {str(e)}", "GeoInfoSystem", Qgis.Critical)

        self.iface.messageBar().pushMessage("GeoInfoSystem", f"Successfully imported {total_sync_count} features.", level=Qgis.Success)
        self.refresh_data()

    def synchronize_changes(self):
        """Finds all vector layers added by the plugin and syncs changes to the server."""
        QgsMessageLog.logMessage("GeoInfoSystem: Starting synchronization...", "GeoInfoSystem", Qgis.Info)
        
        layers = QgsProject.instance().mapLayers().values()
        total_sync_count = 0
        
        # Determine fallback project ID
        fallback_project_id = None
        for i in range(self.model.rowCount()):
            item = self.model.item(i)
            if item and item.data(Qt.UserRole) and item.data(Qt.UserRole).get('id'):
                fallback_project_id = item.data(Qt.UserRole).get('id')
                break

        for layer in layers:
            if not isinstance(layer, QgsVectorLayer):
                continue
            
            project_id = layer.customProperty("geoinfo_project_id") or fallback_project_id
            folder_id = layer.customProperty("geoinfo_folder_id")
            if folder_id == "": folder_id = None
            
            fields = [f.name().lower() for f in layer.fields()]
            if not project_id or 'name' not in fields:
                continue
            
            geom_type = layer.geometryType()
            api_type = None
            if geom_type == 0: api_type = "points"
            elif geom_type == 1: api_type = "multilines"
            elif geom_type == 2: api_type = "polygons"
            
            if not api_type: continue

            layer.startEditing()
            try:
                # Ensure we have a suitable ID field
                id_field_idx, id_field_name = self.get_or_create_external_id_field(layer)
                
                features = list(layer.getFeatures())
                
                for feature in features:
                    # Determine if new or update
                    feat_id = feature.attribute(id_field_name) if id_field_idx != -1 else None
                    
                    import re
                    is_uuid = feat_id and feat_id != NULL and bool(re.match(r'^[0-9a-f]{8}-', str(feat_id), re.I))
                    is_new = not is_uuid
                    
                    # If updating, ensure the DTO has the ID
                    dto = self.layer_factory.export_feature_to_dto(feature, project_id, folder_id, api_type=api_type, source_layer=layer)
                    if not dto: continue
                    
                    if is_uuid:
                        dto["id"] = str(feat_id)
                    
                    result = self.api.sync_feature(api_type, dto, is_new=is_new)
                    if result:
                        total_sync_count += 1
                        if is_new and id_field_idx != -1:
                            new_id = str(result.get('id'))
                            layer.changeAttributeValue(feature.id(), id_field_idx, new_id)
                
                layer.commitChanges()
            except Exception as e:
                layer.rollBack()
                QgsMessageLog.logMessage(f"GeoInfoSystem: Error during sync of '{layer.name()}': {str(e)}", "GeoInfoSystem", Qgis.Critical)

        self.iface.messageBar().pushMessage("GeoInfoSystem", f"Synchronized {total_sync_count} features.", level=Qgis.Success)

    def refresh_data(self):
        """Loads projects and layers from API and builds a hierarchical tree."""
        self.model.removeRows(0, self.model.rowCount())
        
        projects = self.api.get_projects()
        imagery_layers = self.api.get_imagery_layers()
        terrain_layers = self.api.get_terrain_layers()

        # 1. Imagery Layers Folder (Global Group)
        wms_root = QStandardItem("Imagery Layers")
        wms_root.setData({'type': 'group'}, Qt.UserRole)
        self.model.appendRow([wms_root, QStandardItem("Global Group")])
        
        for l in imagery_layers:
            l_item = QStandardItem(l.get('name', 'Unnamed Imagery'))
            l_item.setData(l, Qt.UserRole)
            l_item.setEditable(False)
            wms_root.appendRow([l_item, QStandardItem("Imagery")])
            
            # Sub-options for Imagery: WMS and COG
            wms_sub = QStandardItem("WMS (Visual)")
            wms_sub.setData({**l, 'layer_mode': 'wms'}, Qt.UserRole)
            l_item.appendRow([wms_sub, QStandardItem("WMS")])
            
            cog_sub = QStandardItem("COG (Analytics)")
            cog_sub.setData({**l, 'layer_mode': 'cog'}, Qt.UserRole)
            l_item.appendRow([cog_sub, QStandardItem("COG")])

        # 2. Terrain Layers Folder (Global Group)
        terrain_root = QStandardItem("Terrain Layers")
        terrain_root.setData({'type': 'group'}, Qt.UserRole)
        self.model.appendRow([terrain_root, QStandardItem("Global Group")])
        
        for t in terrain_layers:
            t_item = QStandardItem(t.get('title', 'Unnamed Terrain'))
            t_item.setData(t, Qt.UserRole)
            t_item.setEditable(False)
            terrain_root.appendRow([t_item, QStandardItem("Terrain")])

        # 3. Projects and their Vectors
        for p in projects:
            project_id = p.get('id')
            p_item = QStandardItem(p.get('name', 'Unnamed Project'))
            p_item.setData(p, Qt.UserRole)
            p_item.setEditable(False)
            self.model.appendRow([p_item, QStandardItem("Project")])
            
            # Vectors Folder (Hierarchical)
            vectors_root = QStandardItem("Vectors")
            vectors_root.setData({'type': 'group'}, Qt.UserRole)
            p_item.appendRow([vectors_root, QStandardItem("Folder")])
            
            self._build_vector_hierarchy(project_id, vectors_root)

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

        # Case 1: Imagery Layer (WMS or COG)
        if 'layerName' in data:
            mode = data.get('layer_mode', 'wms')
            if mode == 'cog':
                self.layer_factory.add_cog_layer(data)
            else:
                self.layer_factory.add_wms_layer(data)
        
        # Case 2: Terrain Layer
        elif 'terrainUrl' in data:
            self.layer_factory.add_terrain_layer(data)
        
        # Case 3: Vector Object (Existing or New)
        else:
            # If it's a new object created in UI, add basic fields
            if data.get('is_new'):
                # Initialize empty structure if geom missing
                if 'geom' not in data:
                    geom_map = {"Point": "POINT EMPTY", "Line": "LINESTRING EMPTY", "Polygon": "POLYGON EMPTY"}
                    data['geom'] = geom_map.get(data.get('type'), "POINT EMPTY")
                
                # Create the layer
                layer = self.layer_factory.add_single_vector_object(data)
                if layer and data.get('project_id'):
                    layer.setCustomProperty("geoinfo_project_id", str(data.get('project_id')))
            else:
                self.layer_factory.add_single_vector_object(data)

    def add_selected_folder(self):
        """Future: Add all objects within a folder/project."""
        pass
