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
from .raster_upload_dialog import RasterUploadDialog
from .shapefile_upload_dialog import ShapefileUploadDialog
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

        # Raster/Vector Import Buttons
        import_btns = QHBoxLayout()
        self.bind_btn = QPushButton("Import Vectors")
        self.bind_btn.setToolTip("Bind selected QGIS vector layers to the selected folder/project in the tree.")
        self.bind_btn.clicked.connect(self.bind_layers_to_project)
        import_btns.addWidget(self.bind_btn)

        self.upload_shapefile_btn = QPushButton("Upload Shapefile (.zip)")
        self.upload_shapefile_btn.setToolTip("Upload a local Shapefile ZIP package to convert and import into project layers.")
        self.upload_shapefile_btn.clicked.connect(self.show_shapefile_upload_dialog)
        import_btns.addWidget(self.upload_shapefile_btn)

        self.upload_raster_btn = QPushButton("Upload Raster")
        self.upload_raster_btn.setToolTip("Upload a GeoTIFF raster layer from QGIS to the system.")
        self.upload_raster_btn.clicked.connect(self.show_raster_upload_dialog)
        import_btns.addWidget(self.upload_raster_btn)
        
        self.btn_layout.addLayout(import_btns)

        self.layout.addLayout(self.btn_layout)
        self.setWidget(self.content_widget)

    def show_raster_upload_dialog(self):
        """Opens the dialog to upload a raster layer."""
        dialog = RasterUploadDialog(self.iface, self.api, self)
        if dialog.exec_():
            self.refresh_data()

    def show_shapefile_upload_dialog(self):
        """Opens the dialog to upload a Shapefile .zip archive."""
        dialog = ShapefileUploadDialog(self.iface, self.api, self)
        if dialog.exec_():
            self.refresh_data()


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

        # 3. Projects and their Layers / Folders / Objects
        for p in projects:
            project_id = p.get('id')
            p_item = QStandardItem(p.get('name', 'Unnamed Project'))
            p_item.setData(p, Qt.UserRole)
            p_item.setEditable(False)
            self.model.appendRow([p_item, QStandardItem("Project")])
            
            self._build_project_hierarchy(project_id, p_item)

    def _build_project_hierarchy(self, project_id, project_item):
        """Builds the Layer -> Folder -> Vector/Raster object structure for a project."""
        layers = self.api.get_project_layers(project_id)
        folders = self.api.get_folders(project_id)
        points = self.api.get_points(project_id)
        multilines = self.api.get_multilines(project_id)
        polygons = self.api.get_polygons(project_id)

        all_vectors = []
        for p in points: all_vectors.append({**p, 'type': 'Point'})
        for m in multilines: all_vectors.append({**m, 'type': 'MultiLineString'})
        for poly in polygons: all_vectors.append({**poly, 'type': 'Polygon'})

        # If no logical layers exist yet, fallback to default Layer node
        if not layers:
            layers = [{'id': None, 'name': 'Default Layer', 'layerType': 'VECTOR'}]

        known_layer_ids = set(str(l.get('id')) for l in layers if l.get('id'))

        # Process each logical layer in the project
        for idx, layer in enumerate(layers):
            layer_id = layer.get('id')
            layer_name = layer.get('name', 'Unnamed Layer')
            layer_type = layer.get('layerType', 'VECTOR')

            layer_item = QStandardItem(layer_name)
            layer_item.setData({**layer, 'type': 'layer', 'projectId': project_id}, Qt.UserRole)
            layer_item.setEditable(False)
            project_item.appendRow([layer_item, QStandardItem(f"Layer ({layer_type})")])

            # Filter folders: match layer_id OR assign to first layer if folder has no valid layer_id
            layer_folders = []
            for f in folders:
                f_lid = str(f.get('layerId', '')) if f.get('layerId') else None
                if f_lid == str(layer_id):
                    layer_folders.append(f)
                elif idx == 0 and (not f_lid or f_lid not in known_layer_ids):
                    layer_folders.append(f)

            folder_items = {}
            for f in layer_folders:
                f_item = QStandardItem(f.get('name', 'Unnamed Folder'))
                f_item.setData({**f, 'type': 'folder', 'projectId': project_id}, Qt.UserRole)
                folder_items[str(f.get('id'))] = f_item

            # Build nested folder hierarchy under layer
            for f in layer_folders:
                f_id = str(f.get('id'))
                p_id = str(f.get('parentId')) if f.get('parentId') else None
                f_item = folder_items[f_id]

                if p_id and p_id in folder_items:
                    folder_items[p_id].appendRow([f_item, QStandardItem("Folder")])
                else:
                    layer_item.appendRow([f_item, QStandardItem("Folder")])

            # Filter vectors: match layer_id OR assign to first layer if vector has no valid layer_id
            layer_vectors = []
            for v in all_vectors:
                v_lid = str(v.get('layerId', '')) if v.get('layerId') else None
                if v_lid == str(layer_id):
                    layer_vectors.append(v)
                elif idx == 0 and (not v_lid or v_lid not in known_layer_ids):
                    layer_vectors.append(v)

            for v in layer_vectors:
                v_item = QStandardItem(v.get('name', 'Vector Feature'))
                v_item.setData({**v, 'projectId': project_id}, Qt.UserRole)
                v_item.setEditable(False)

                f_id = str(v.get('folderId')) if v.get('folderId') else None
                if f_id and f_id in folder_items:
                    folder_items[f_id].appendRow([v_item, QStandardItem(v['type'])])
                else:
                    layer_item.appendRow([v_item, QStandardItem(v['type'])])

            # Add project rasters (either to folder or to layer root)
            if layer_id:
                project_rasters = self.api.get_project_rasters_by_layer(layer_id)
                for r in project_rasters:
                    r_item = QStandardItem(r.get('name', 'Project Raster'))
                    r_item.setData({**r, 'type': 'ProjectRaster', 'layer_mode': 'cog', 'projectId': project_id}, Qt.UserRole)
                    r_item.setEditable(False)

                    f_id = str(r.get('folderId')) if r.get('folderId') else None
                    if f_id and f_id in folder_items:
                        folder_items[f_id].appendRow([r_item, QStandardItem("Raster (COG)")])
                    else:
                        layer_item.appendRow([r_item, QStandardItem("Raster (COG)")])


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
                self.layer_factory.add_single_vector_object(data)


    def add_selected_folder(self):
        """Future: Add all objects within a folder/project."""
        pass

