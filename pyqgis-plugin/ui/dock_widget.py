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
from .jobs_manager_dialog import JobsManagerDialog

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
        self.tree_view.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tree_view.customContextMenuRequested.connect(self.show_tree_context_menu)
        self.tree_view.doubleClicked.connect(self.on_tree_double_clicked)
        self.layout.addWidget(self.tree_view)

        # Buttons layout
        self.btn_layout = QHBoxLayout()
        
        self.refresh_btn = QPushButton("Refresh")
        self.refresh_btn.clicked.connect(self.refresh_data)
        self.btn_layout.addWidget(self.refresh_btn)
        
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
            l_item.setData({**l, 'layer_mode': 'cog'}, Qt.UserRole)
            l_item.setEditable(False)
            wms_root.appendRow([l_item, QStandardItem("Imagery (COG)")])


        # 2. Terrain Layers Folder (Global Group)
        terrain_root = QStandardItem("Terrain Layers")
        terrain_root.setData({'type': 'group'}, Qt.UserRole)
        self.model.appendRow([terrain_root, QStandardItem("Global Group")])
        
        for t in terrain_layers:
            t_item = QStandardItem(t.get('title', 'Unnamed Terrain'))
            t_item.setData(t, Qt.UserRole)
            t_item.setEditable(False)
            terrain_root.appendRow([t_item, QStandardItem("Terrain")])

        # 3. Global Jobs Node
        jobs_root = QStandardItem("Global Jobs (Задачи обработки)")
        jobs_root.setData({'type': 'global_jobs'}, Qt.UserRole)
        jobs_root.setEditable(False)
        self.model.appendRow([jobs_root, QStandardItem("Global Tasks")])

        # 4. Projects and their Layers / Folders / Objects
        for p in projects:
            project_id = p.get('id')
            p_item = QStandardItem(p.get('name', 'Unnamed Project'))
            p_item.setData(p, Qt.UserRole)
            p_item.setEditable(False)
            self.model.appendRow([p_item, QStandardItem("Project")])
            
            self._build_project_hierarchy(project_id, p_item)

    def _build_project_hierarchy(self, project_id, project_item):
        """Builds the Layer -> Folder -> Vector/Raster object structure for a project."""
        # Add Project Jobs node
        jobs_item = QStandardItem("Project Jobs (Задачи проекта)")
        jobs_item.setData({'type': 'project_jobs', 'projectId': project_id, 'projectName': project_item.text()}, Qt.UserRole)
        jobs_item.setEditable(False)
        project_item.appendRow([jobs_item, QStandardItem("Project Tasks")])

        # Fetch the unified hierarchy
        hierarchy = self.api.get_project_hierarchy(project_id)
        if not hierarchy:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to fetch hierarchy for project {project_id}", "GeoInfoSystem", Qgis.Warning)
            return

        try:
            import json
            hierarchy_str = json.dumps(hierarchy, indent=2, ensure_ascii=False)
            QgsMessageLog.logMessage(f"GeoInfoSystem: Received hierarchy JSON for project {project_id}:\n{hierarchy_str}", "GeoInfoSystem", Qgis.Info)
        except Exception as e:
            QgsMessageLog.logMessage(f"GeoInfoSystem: Failed to log hierarchy JSON: {str(e)}", "GeoInfoSystem", Qgis.Warning)

        layers = hierarchy.get('layers', [])
        
        # Helper to recursively add folders
        def add_folder_item(parent_node, folder_data):
            f_item = QStandardItem(folder_data.get('name', 'Unnamed Folder'))
            f_item.setData({**folder_data, 'type': 'folder', 'projectId': project_id}, Qt.UserRole)
            f_item.setEditable(False)
            parent_node.appendRow([f_item, QStandardItem("Folder")])
            
            # Add nested folders
            for sub in folder_data.get('subfolders', []):
                add_folder_item(f_item, sub)
                
            # Add features/rasters in this folder
            for obj in folder_data.get('objects', []):
                add_object_item(f_item, obj)

        # Helper to add objects (features/rasters)
        def add_object_item(parent_node, obj_data):
            obj_type = obj_data.get('type')
            if obj_type == 'Raster':
                r_item = QStandardItem(obj_data.get('name', 'Project Raster'))
                r_item.setData({**obj_data, 'type': 'ProjectRaster', 'layer_mode': 'cog', 'projectId': project_id}, Qt.UserRole)
                r_item.setEditable(False)
                parent_node.appendRow([r_item, QStandardItem("Raster (COG)")])
            else:
                v_item = QStandardItem(obj_data.get('name', 'Vector Feature'))
                v_item.setData({**obj_data, 'projectId': project_id}, Qt.UserRole)
                v_item.setEditable(False)
                parent_node.appendRow([v_item, QStandardItem(obj_type)])

        # Process each logical layer in the project
        for layer in layers:
            layer_id = layer.get('id')
            layer_name = layer.get('name', 'Unnamed Layer')
            layer_type = layer.get('type', 'VECTOR')

            layer_item = QStandardItem(layer_name)
            layer_item.setData({**layer, 'layerType': layer_type, 'type': 'layer', 'projectId': project_id}, Qt.UserRole)
            layer_item.setEditable(False)
            project_item.appendRow([layer_item, QStandardItem(f"Layer ({layer_type})")])

            # 1. Add top-level folders
            for folder in layer.get('folders', []):
                add_folder_item(layer_item, folder)

            # 2. Add top-level objects (not in any folder)
            for obj in layer.get('objects', []):
                add_object_item(layer_item, obj)


    def add_selected_layer(self):
        """Adds the selected item from the tree to the QGIS map."""
        index = self.tree_view.currentIndex()
        if not index.isValid():
            return
            
        item = self.model.itemFromIndex(index)
        data = item.data(Qt.UserRole)
        
        if not data or data.get('type') in ['group', 'folder', 'layer']:
            return

        # Case 1: Imagery / Project Raster Layer (COG)
        if 'cogObjectKey' in data or 'layerName' in data or data.get('type') == 'ProjectRaster' or 'layer_mode' in data:
            self.layer_factory.add_cog_layer(data)

        
        # Case 2: Terrain Layer
        elif 'terrainUrl' in data or ('title' in data and 'id' in data and data.get('type') != 'Point'):
            self.layer_factory.add_terrain_layer(data)
        
        # Case 3: Vector Object
        elif 'geom' in data or data.get('type') in ['Point', 'MultiLineString', 'Polygon'] or data.get('is_new'):
            if data.get('is_new'):
                if 'geom' not in data:
                    geom_map = {"Point": "POINT EMPTY", "Line": "LINESTRING EMPTY", "Polygon": "POLYGON EMPTY"}
                    data['geom'] = geom_map.get(data.get('type'), "POINT EMPTY")
                
                layer = self.layer_factory.add_single_vector_object(data)
                if layer and data.get('project_id'):
                    layer.setCustomProperty("geoinfo_project_id", str(data.get('project_id')))
            else:
                # Retrieve the full vector object (with geometry) from API
                obj_id = data.get('id')
                obj_type = data.get('type')
                full_obj = None
                if obj_type == 'Point':
                    full_obj = self.api.get_point(obj_id)
                elif obj_type == 'MultiLineString':
                    full_obj = self.api.get_multiline(obj_id)
                elif obj_type == 'Polygon':
                    full_obj = self.api.get_polygon(obj_id)
                
                if full_obj:
                    full_obj['type'] = obj_type
                    self.layer_factory.add_single_vector_object(full_obj)
                else:
                    QMessageBox.warning(self, "GeoInfoSystem", f"Не удалось загрузить детальные данные объекта с ID: {obj_id}")


    def add_selected_folder(self):
        """Future: Add all objects within a folder/project."""
        pass

    def on_tree_double_clicked(self, index):
        if not index.isValid():
            return
        item = self.model.itemFromIndex(index)
        data = item.data(Qt.UserRole) or {}
        
        item_type = data.get('type')
        if item_type == 'global_jobs':
            dialog = JobsManagerDialog(self.iface, self.api, parent=self)
            dialog.exec_()
        elif item_type == 'project_jobs':
            project_id = data.get('projectId')
            project_name = data.get('projectName')
            dialog = JobsManagerDialog(self.iface, self.api, project_id=project_id, project_name=project_name, parent=self)
            dialog.exec_()

    def show_tree_context_menu(self, position):
        index = self.tree_view.indexAt(position)
        if not index.isValid():
            return

        item = self.model.itemFromIndex(index)
        data = item.data(Qt.UserRole) or {}
        
        from qgis.PyQt.QtWidgets import QMenu
        menu = QMenu(self)

        item_type = data.get('type')
        can_add = False
        can_sync = False
        
        if item_type not in ['group', 'folder', 'layer', 'global_jobs', 'project_jobs'] and data:
            if ('cogObjectKey' in data or 'layerName' in data or item_type == 'ProjectRaster' or 'layer_mode' in data or
                'terrainUrl' in data or ('title' in data and 'id' in data and item_type != 'Point') or
                'geom' in data or item_type in ['Point', 'MultiLineString', 'Polygon'] or data.get('is_new')):
                can_add = True

        if item_type == 'layer' and data.get('layerType') == 'VECTOR':
            can_sync = True
        elif item_type == 'folder':
            can_sync = True
        elif data.get('projectId') and not item_type: # feature item
            can_sync = True
            
        if can_add:
            add_action = menu.addAction("Добавить на карту (Add to Map)")
            add_action.triggered.connect(self.add_selected_layer)
            
        if can_sync:
            sync_action = menu.addAction("Синхронизировать слой (Sync changes)")
            sync_action.triggered.connect(lambda: self.sync_layer_by_tree_item(item))

        if menu.actions():
            menu.exec_(self.tree_view.viewport().mapToGlobal(position))

    def sync_layer_by_tree_item(self, item):
        data = item.data(Qt.UserRole) or {}
        project_id = data.get('projectId')
        layer_id = None
        folder_id = None
        
        item_type = data.get('type')
        if item_type == 'layer':
            layer_id = data.get('id')
        elif item_type == 'folder':
            folder_id = data.get('id')
            layer_id = data.get('layerId')
        else: # feature item
            layer_id = data.get('layerId')
            folder_id = data.get('folderId')
            
        if not project_id:
            # Find project ID in parents
            parent = item.parent()
            while parent:
                p_data = parent.data(Qt.UserRole) or {}
                if p_data.get('id') and parent.text() != 'Imagery Layers' and parent.text() != 'Terrain Layers':
                    project_id = p_data.get('id')
                    break
                parent = parent.parent()

        if not project_id:
            QMessageBox.warning(self, "GeoInfoSystem", "Не удалось определить целевой проект для синхронизации.")
            return
            
        # Sync only layers in QGIS matching project_id and optionally layer_id / folder_id
        layers = QgsProject.instance().mapLayers().values()
        sync_count = 0
        
        for layer in layers:
            if not isinstance(layer, QgsVectorLayer):
                continue
                
            lyr_project_id = layer.customProperty("geoinfo_project_id")
            lyr_folder_id = layer.customProperty("geoinfo_folder_id")
            
            if lyr_project_id != str(project_id):
                continue
                
            if folder_id and lyr_folder_id != str(folder_id):
                continue
                
            geom_type = layer.geometryType()
            api_type = None
            if geom_type == 0: api_type = "points"
            elif geom_type == 1: api_type = "multilines"
            elif geom_type == 2: api_type = "polygons"
            
            if not api_type:
                continue
                
            layer.startEditing()
            try:
                id_field_idx, id_field_name = self.get_or_create_external_id_field(layer)
                features = list(layer.getFeatures())
                
                for feature in features:
                    feat_id = feature.attribute(id_field_name) if id_field_idx != -1 else None
                    
                    import re
                    is_uuid = feat_id and feat_id != NULL and bool(re.match(r'^[0-9a-f]{8}-', str(feat_id), re.I))
                    is_new = not is_uuid
                    
                    dto = self.layer_factory.export_feature_to_dto(feature, project_id, folder_id, api_type=api_type, source_layer=layer)
                    if not dto:
                       continue
                       
                    if is_uuid:
                       dto["id"] = str(feat_id)
                       
                    result = self.api.sync_feature(api_type, dto, is_new=is_new)
                    if result:
                       sync_count += 1
                       if is_new and id_field_idx != -1:
                           new_id = str(result.get('id'))
                           layer.changeAttributeValue(feature.id(), id_field_idx, new_id)
                layer.commitChanges()
            except Exception as e:
                layer.rollBack()
                QgsMessageLog.logMessage(f"GeoInfoSystem: Error during single layer sync of '{layer.name()}': {str(e)}", "GeoInfoSystem", Qgis.Critical)

        self.iface.messageBar().pushMessage("GeoInfoSystem", f"Синхронизировано объектов: {sync_count}.", level=Qgis.Success)

