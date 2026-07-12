<template>
  <v-list density="compact" nav class="pa-0" @dragover.prevent @drop="onDropToRoot">
    <!-- Header with Actions -->
    <v-list-item 
      class="px-2" 
      :active="selectedFolderId === null" 
      @click="selectRoot"
      prepend-icon="mdi-layers-triple"
    >
      <template v-slot:prepend>
        <v-icon color="primary" class="mr-2">mdi-layers-triple</v-icon>
        <span class="text-subtitle-2 font-weight-bold">Слои и объекты</span>
      </template>
      <template v-slot:append>
        <v-btn
          icon="mdi-layers-plus"
          variant="text"
          density="compact"
          color="primary"
          title="Создать слой"
          @click.stop="openCreateLayerDialog"
        ></v-btn>
      </template>
    </v-list-item>
    <v-divider></v-divider>

    <!-- Layers -->
    <template v-for="layer in layers" :key="layer.id">
      <v-list-group :value="layer.id">
        <template v-slot:activator="{ props: vProps }">
          <v-list-item
            v-bind="vProps"
            prepend-icon="mdi-layers-triple"
            :title="layer.name"
            class="layer-item-header"
          >
            <template v-slot:append>
              <v-chip size="x-small" :color="layer.type === 'VECTOR' ? 'primary' : 'secondary'" class="mr-2">
                {{ layer.type === 'VECTOR' ? 'Векторный' : 'Растровый' }}
              </v-chip>
              <v-btn
                icon="mdi-folder-plus"
                variant="text"
                density="compact"
                color="primary"
                title="Создать папку в слое"
                @click.stop="openCreateFolderForLayer(layer.id)"
                class="mr-1"
              ></v-btn>
              <v-btn
                :icon="isLayerVisible(layer.id) ? 'mdi-eye' : 'mdi-eye-off'"
                variant="text"
                density="compact"
                :color="isLayerVisible(layer.id) ? 'primary' : 'grey'"
                title="Показать/скрыть все объекты слоя"
                @click.stop="toggleLayerVisibility(layer.id)"
              ></v-btn>
              <v-btn
                icon="mdi-delete"
                variant="text"
                density="compact"
                color="error"
                title="Удалить слой"
                @click.stop="confirmDeleteLayer(layer)"
              ></v-btn>
            </template>
          </v-list-item>
        </template>

        <!-- Folders in this layer -->
        <template v-for="folder in getLayerFolders(layer.id)" :key="folder.id">
          <FolderItem :folder="folder" :all-folders="folders" :objects="allObjects" />
        </template>

        <!-- Objects directly in this layer (no folder) -->
        <template v-for="obj in getLayerObjects(layer.id)" :key="obj.id">
          <v-list-item
            v-if="obj.type !== 'Raster'"
            @click="selectObject(obj)"
            :active="selectedFeatureId === obj.id"
            :prepend-icon="getIcon(obj.type)"
            :title="obj.name"
            :subtitle="obj.status"
            draggable="true"
            @dragstart="onDragStart($event, obj, 'object')"
            class="draggable-item ml-4"
          >
            <template v-slot:append>
              <v-btn
                icon="mdi-eye"
                variant="text"
                density="compact"
                :color="isVisible(obj) ? 'primary' : 'grey'"
                @click.stop="toggleVisibility(obj)"
              ></v-btn>
            </template>
          </v-list-item>

          <!-- Raster item directly under layer -->
          <div v-else class="pr-2 py-1 border-bottom d-flex flex-column" style="margin-left: 24px;">
            <div class="d-flex align-center justify-space-between">
              <div class="d-flex align-center">
                <v-icon color="secondary" class="mr-2">mdi-image-filter-hdr</v-icon>
                <span class="text-body-2 font-weight-medium">{{ obj.name }}</span>
              </div>
              <div class="d-flex align-center">
                <v-btn
                  icon="mdi-palette"
                  variant="text"
                  density="comfortable"
                  color="primary"
                  title="Сменить стиль"
                  @click.stop="showStyleEditor = true"
                ></v-btn>
                <v-btn
                  icon="mdi-eye"
                  variant="text"
                  density="comfortable"
                  :color="isRasterVisible(obj) ? 'primary' : 'grey'"
                  title="Видимость"
                  @click.stop="toggleRasterVisibility(obj)"
                ></v-btn>
              </div>
            </div>
            
            <!-- Opacity Slider -->
            <div v-if="isRasterVisible(obj)" class="d-flex align-center pl-6 pr-2">
              <v-icon size="small" class="mr-2 text-grey-darken-1">mdi-opacity</v-icon>
              <v-slider
                :model-value="getRasterOpacity(obj)"
                @update:model-value="val => setRasterOpacity(obj, val)"
                min="0"
                max="100"
                step="1"
                hide-details
                dense
                color="secondary"
                class="flex-grow-1"
              ></v-slider>
              <span class="text-caption ml-2 text-grey-darken-2" style="min-width: 30px; text-align: right;">{{ getRasterOpacity(obj) }}%</span>
            </div>

            <!-- Style Selector Block -->
            <div v-if="isRasterVisible(obj)" class="w-100 pl-6 pr-2 mt-1 d-flex flex-column">
              <v-checkbox
                :model-value="getUseTiTilerColormap(obj)"
                label="Встроенная шкала TiTiler"
                density="compact"
                hide-details
                class="mt-0 mb-1"
                style="font-size: 11px;"
                @update:model-value="val => toggleTiTilerColormap(obj, !!val)"
              />

              <v-select
                v-if="getUseTiTilerColormap(obj)"
                :model-value="obj.colormapId || 'viridis'"
                :items="titilerColormaps"
                label="Шкала TiTiler"
                density="compact"
                variant="outlined"
                hide-details
                style="font-size: 11px;"
                class="mb-2"
                @update:model-value="val => handleLayerColormapChange(obj.id, val)"
              />

              <v-select
                v-else
                :model-value="obj.style?.id || null"
                :items="customStylesSelectItems"
                item-title="title"
                item-value="id"
                label="Стиль интерполяции"
                density="compact"
                variant="outlined"
                hide-details
                clearable
                placeholder="Без стиля (Полутоновый)"
                style="font-size: 11px;"
                class="mb-2"
                @update:model-value="val => handleLayerCustomStyleChange(obj.id, val)"
              />

              <v-select
                :model-value="obj.resampling || 'bilinear'"
                :items="['nearest', 'bilinear', 'cubic', 'cubic_spline', 'lanczos', 'average', 'mode']"
                label="Метод resampling"
                density="compact"
                variant="outlined"
                hide-details
                style="font-size: 11px;"
                class="mb-2"
                @update:model-value="val => handleLayerResamplingChange(obj.id, val)"
              />
            </div>
          </div>
        </template>
      </v-list-group>
    </template>
    
    <!-- Empty space drop zone indicator -->
    <v-list-item v-if="layers.length === 0" class="text-center text-grey-darken-1 text-caption py-4">
        Нет созданных слоев
    </v-list-item>
  </v-list>

  <!-- Create Layer Dialog -->
  <v-dialog v-model="layerDialog" max-width="500px">
    <v-card>
      <v-card-title class="bg-primary text-white py-3 px-4">
        <v-icon class="mr-2">mdi-layers-plus</v-icon>
        <span class="font-weight-bold">Создать новый слой</span>
      </v-card-title>
      <v-card-text class="pa-4">
        <v-form ref="layerForm" v-model="layerFormValid">
          <v-text-field
            v-model="newLayer.name"
            label="Название слоя"
            variant="outlined"
            density="comfortable"
            :rules="[v => !!v || 'Название обязательно']"
          ></v-text-field>
          <v-select
            v-model="newLayer.type"
            :items="[
              { title: 'Векторный слой', value: 'VECTOR' },
              { title: 'Растровый слой', value: 'RASTER' }
            ]"
            item-title="title"
            item-value="value"
            label="Тип слоя"
            variant="outlined"
            density="comfortable"
            :rules="[v => !!v || 'Тип обязателен']"
          ></v-select>
        </v-form>
      </v-card-text>
      <v-card-actions class="pa-4 pt-0">
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="layerDialog = false">Отмена</v-btn>
        <v-btn color="primary" variant="elevated" @click="saveLayer" :loading="savingLayer" :disabled="!layerFormValid">Создать</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <RasterStyleEditorDialog v-model="showStyleEditor" @styles-updated="fetchStyles" />

  <FolderEditDialog
    v-model="folderDialog"
    :project-id="projectId || ''"
    :layer-id="activeLayerIdForFolder"
    @saved="onFolderSaved"
  />

  <!-- Delete Layer Confirmation Dialog -->
  <v-dialog v-model="deleteLayerDialog" max-width="500px">
    <v-card>
      <v-card-title class="bg-error text-white py-3 px-4">
        <v-icon class="mr-2">mdi-alert-circle</v-icon>
        <span class="font-weight-bold">Удалить слой</span>
      </v-card-title>
      <v-card-text class="pa-4">
        <p>Вы действительно хотите удалить слой <strong>"{{ layerToDelete?.name }}"</strong>?</p>
        <p class="text-error mt-2">Это действие необратимо. Все связанные объекты (точки, линии, полигоны, растры) и их файлы в хранилище будут удалены.</p>
      </v-card-text>
      <v-card-actions class="pa-4 pt-0">
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="deleteLayerDialog = false">Отмена</v-btn>
        <v-btn color="error" variant="elevated" @click="deleteLayer" :loading="deletingLayer">Удалить</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, provide, onMounted } from 'vue';
import { useStore } from 'vuex';
import type { GeoFolder, ProjectPoint, ProjectMultiline, ProjectPolygon, Layer } from '@/types/api';
import FolderItem from './FolderItem.vue';
import FolderEditDialog from './FolderEditDialog.vue';
import RasterStyleEditorDialog from '../imagery/RasterStyleEditorDialog.vue';
import RasterStyleService from '@/services/raster-style.service';
import geodataService from '@/services/geodata.service';

const store = useStore();

const projectId = computed(() => store.state.geodata.selectedProjectId);
const folders = computed<GeoFolder[]>(() => store.state.geodata.folders);
const points = computed(() => store.state.geodata.points.map((p: ProjectPoint) => ({ ...p, type: 'Point' })));
const multilines = computed(() => store.state.geodata.multilines.map((m: ProjectMultiline) => ({ ...m, type: 'MultiLineString' })));
const polygons = computed(() => store.state.geodata.polygons.map((p: ProjectPolygon) => ({ ...p, type: 'Polygon' })));
const rasters = computed(() => (store.state.geodata.projectRasters?.content || []).map((r: any) => ({ ...r, type: 'Raster' })));

const allObjects = computed(() => {
  const pts = points.value.map((p: any) => {
    let layerId = p.layerId;
    if (!layerId && p.folderId) {
      const folder = folders.value.find(f => f.id === p.folderId);
      if (folder) layerId = folder.layerId;
    }
    if (!layerId) {
      const vectorLayer = store.state.geodata.layers.find((l: any) => l.type === 'VECTOR');
      if (vectorLayer) layerId = vectorLayer.id;
    }
    return { ...p, layerId };
  });

  const mls = multilines.value.map((m: any) => {
    let layerId = m.layerId;
    if (!layerId && m.folderId) {
      const folder = folders.value.find(f => f.id === m.folderId);
      if (folder) layerId = folder.layerId;
    }
    if (!layerId) {
      const vectorLayer = store.state.geodata.layers.find((l: any) => l.type === 'VECTOR');
      if (vectorLayer) layerId = vectorLayer.id;
    }
    return { ...m, layerId };
  });

  const pgs = polygons.value.map((p: any) => {
    let layerId = p.layerId;
    if (!layerId && p.folderId) {
      const folder = folders.value.find(f => f.id === p.folderId);
      if (folder) layerId = folder.layerId;
    }
    if (!layerId) {
      const vectorLayer = store.state.geodata.layers.find((l: any) => l.type === 'VECTOR');
      if (vectorLayer) layerId = vectorLayer.id;
    }
    return { ...p, layerId };
  });

  const rsts = rasters.value.map((r: any) => {
    let layerId = r.layerId;
    if (!layerId && r.folderId) {
      const folder = folders.value.find(f => f.id === r.folderId);
      if (folder) layerId = folder.layerId;
    }
    if (!layerId) {
      const rasterLayer = store.state.geodata.layers.find((l: any) => l.type === 'RASTER');
      if (rasterLayer) layerId = rasterLayer.id;
    }
    return { ...r, layerId };
  });

  return [...pts, ...mls, ...pgs, ...rsts];
});

const selectedFeatureId = computed(() => store.state.geodata.selectedFeatureId);
const selectedFolderId = computed(() => store.state.geodata.selectedFolderId);

const layers = computed<Layer[]>(() => store.state.geodata.layers || []);

const getLayerFolders = (layerId: string) => {
  return folders.value.filter((f: GeoFolder) => f.layerId === layerId && !f.parentId);
};

const getLayerObjects = (layerId: string) => {
  return allObjects.value.filter((obj: any) => obj.layerId === layerId && !obj.folderId);
};

const getIcon = (type: string) => {
  if (type === 'Point') return 'mdi-map-marker';
  if (type === 'MultiLineString') return 'mdi-vector-polyline';
  return 'mdi-vector-polygon';
};

const selectObject = (obj: any) => {
  store.commit('geodata/SET_SELECTED_FOLDER_ID', obj.folderId);
  store.dispatch('geodata/selectFeature', { id: obj.id, source: 'list' });
};

const selectRoot = () => {
  store.commit('geodata/SET_SELECTED_FOLDER_ID', null);
};

const isVisible = (obj: any) => {
  return obj.characteristics?.visible !== false;
};

const toggleVisibility = (obj: any) => {
  store.dispatch('geodata/toggleFeatureVisibility', { id: obj.id, type: obj.type });
};

const availableStyles = computed(() => store.state.geodata.styles || []);
const titilerColormaps = ref<string[]>([]);
const showStyleEditor = ref(false);

const fetchStyles = () => {
  store.dispatch('geodata/fetchStyles');
};

const fetchColormaps = async () => {
  try {
    titilerColormaps.value = await RasterStyleService.getTiTilerColorMaps();
  } catch (err) {
    titilerColormaps.value = ['cividis', 'inferno', 'magma', 'plasma', 'rdylgn', 'spectral', 'terrain', 'viridis'];
  }
};

const useTiTilerMap = ref<Record<string, boolean>>({});

const getUseTiTilerColormap = (layer: any) => {
  if (useTiTilerMap.value[layer.id] !== undefined) {
    return useTiTilerMap.value[layer.id];
  }
  const initialVal = !!(layer.colormapId || !layer.style?.id);
  useTiTilerMap.value[layer.id] = initialVal;
  return initialVal;
};

const toggleTiTilerColormap = async (layer: any, val: boolean) => {
  useTiTilerMap.value[layer.id] = val;
  const layerInfo = store.state.geodata.projectRasters?.content?.find((l: any) => l.id === layer.id);
  if (!layerInfo) return;

  if (val) {
    const colormapId = layer.colormapId || 'viridis';
    store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId: layer.id, style: null, colormapId });
    await store.dispatch('geodata/updateProjectRaster', {
      layerData: { ...layerInfo, style: null, colormapId },
      page: 0, size: 100
    });
  } else {
    store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId: layer.id, style: null, colormapId: null });
    await store.dispatch('geodata/updateProjectRaster', {
      layerData: { ...layerInfo, style: null, colormapId: null },
      page: 0, size: 100
    });
  }
};

const customStylesSelectItems = computed(() => {
  return availableStyles.value.map((s: any) => ({
    title: s.title,
    id: s.id
  }));
});

const isRasterVisible = (obj: any) => {
  return store.state.geodata.visibleRasterIds.includes(obj.id);
};

const toggleRasterVisibility = (obj: any) => {
  store.commit('geodata/TOGGLE_RASTER_VISIBILITY', obj.id);
};

const getRasterOpacity = (obj: any) => {
  return store.state.geodata.rasterOpacities[obj.id] ?? 100;
};

const setRasterOpacity = (obj: any, val: number) => {
  store.commit('geodata/SET_RASTER_OPACITY', { rasterId: obj.id, opacity: val });
};

const handleLayerCustomStyleChange = async (layerId: string, styleId: string | null) => {
  const layerInfo = store.state.geodata.projectRasters?.content?.find((l: any) => l.id === layerId);
  if (!layerInfo) return;

  if (!styleId) {
    store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId, style: null, colormapId: null });
    await store.dispatch('geodata/updateProjectRaster', {
      layerData: { ...layerInfo, style: null, colormapId: null },
      page: 0, size: 100
    });
    return;
  }

  let styleObj = null;
  try {
    const res = await RasterStyleService.getRasterStyleById(styleId);
    styleObj = res.data;
  } catch (err) {
    console.error(err);
    styleObj = { id: styleId };
  }
  store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId, style: styleObj, colormapId: null });
  await store.dispatch('geodata/updateProjectRaster', {
    layerData: { ...layerInfo, style: styleObj, colormapId: null },
    page: 0, size: 100
  });
};

const handleLayerColormapChange = async (layerId: string, colormapId: string | null) => {
  const layerInfo = store.state.geodata.projectRasters?.content?.find((l: any) => l.id === layerId);
  if (!layerInfo) return;

  store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId, style: null, colormapId });
  await store.dispatch('geodata/updateProjectRaster', {
    layerData: { ...layerInfo, style: null, colormapId },
    page: 0, size: 100
  });
};

const handleLayerResamplingChange = async (layerId: string, resampling: string) => {
  const layerInfo = store.state.geodata.projectRasters?.content?.find((l: any) => l.id === layerId);
  if (!layerInfo) return;

  store.commit('geodata/UPDATE_PROJECT_RASTER_STYLE', { layerId, resampling });
  await store.dispatch('geodata/updateProjectRaster', {
    layerData: { ...layerInfo, resampling },
    page: 0, size: 100
  });
};

provide('availableStyles', availableStyles);
provide('titilerColormaps', titilerColormaps);
provide('customStylesSelectItems', customStylesSelectItems);
provide('getUseTiTilerColormap', getUseTiTilerColormap);
provide('toggleTiTilerColormap', toggleTiTilerColormap);
provide('handleLayerCustomStyleChange', handleLayerCustomStyleChange);
provide('handleLayerColormapChange', handleLayerColormapChange);
provide('handleLayerResamplingChange', handleLayerResamplingChange);
provide('openStyleEditor', () => { showStyleEditor.value = true; });
provide('isRasterVisible', isRasterVisible);
provide('toggleRasterVisibility', toggleRasterVisibility);
provide('getRasterOpacity', getRasterOpacity);
provide('setRasterOpacity', setRasterOpacity);

onMounted(() => {
  fetchStyles();
  fetchColormaps();
});

// --- Layer Folder and Visibility Management ---
const folderDialog = ref(false);
const activeLayerIdForFolder = ref<string | null>(null);

const openCreateFolderForLayer = (layerId: string) => {
  activeLayerIdForFolder.value = layerId;
  folderDialog.value = true;
};

const onFolderSaved = () => {
  if (projectId.value) {
    store.dispatch('geodata/fetchFolders', projectId.value);
  }
};

const isLayerVisible = (layerId: string) => {
  const layerObjects = getLayerObjects(layerId);
  const layerFolders = folders.value.filter(f => f.layerId === layerId);
  const folderIds = layerFolders.map(f => f.id);
  const folderObjects = allObjects.value.filter(obj => folderIds.includes(obj.folderId));
  
  const allLayerObjects = [...layerObjects, ...folderObjects];
  if (allLayerObjects.length === 0) return true;
  
  return allLayerObjects.some(obj => {
    if (obj.type === 'Raster') {
      return isRasterVisible(obj);
    } else {
      return isVisible(obj);
    }
  });
};

const toggleLayerVisibility = (layerId: string) => {
  const visible = isLayerVisible(layerId);
  const targetVisible = !visible;
  
  const layerObjects = getLayerObjects(layerId);
  const layerFolders = folders.value.filter(f => f.layerId === layerId);
  const folderIds = layerFolders.map(f => f.id);
  const folderObjects = allObjects.value.filter(obj => folderIds.includes(obj.folderId));
  
  const allLayerObjects = [...layerObjects, ...folderObjects];
  
  for (const obj of allLayerObjects) {
    if (obj.type === 'Raster') {
      const isCurrentVisible = isRasterVisible(obj);
      if (isCurrentVisible !== targetVisible) {
        toggleRasterVisibility(obj);
      }
    } else {
      const isCurrentVisible = isVisible(obj);
      if (isCurrentVisible !== targetVisible) {
        toggleVisibility(obj);
      }
    }
  }
};

// --- Layer Management ---

const layerDialog = ref(false);
const layerFormValid = ref(false);
const savingLayer = ref(false);
const newLayer = ref({
  name: '',
  type: 'VECTOR'
});

const openCreateLayerDialog = () => {
  newLayer.value = {
    name: '',
    type: 'VECTOR'
  };
  layerDialog.value = true;
};

const saveLayer = async () => {
  if (!projectId.value) return;
  savingLayer.value = true;
  try {
    await geodataService.createLayer({
      projectId: projectId.value,
      name: newLayer.value.name,
      type: newLayer.value.type as 'VECTOR' | 'RASTER'
    });
    layerDialog.value = false;
    
    // Refresh layers, folders and project rasters to reflect new layers
    store.dispatch('geodata/fetchProjectLayers', projectId.value);
    store.dispatch('geodata/fetchFolders', projectId.value);
    store.dispatch('geodata/fetchProjectRasters');
  } catch (err) {
    console.error('Failed to create layer:', err);
  } finally {
    savingLayer.value = false;
  }
};

const deleteLayerDialog = ref(false);
const deletingLayer = ref(false);
const layerToDelete = ref<Layer | null>(null);

const confirmDeleteLayer = (layer: Layer) => {
  layerToDelete.value = layer;
  deleteLayerDialog.value = true;
};

const deleteLayer = async () => {
  if (!layerToDelete.value) return;
  deletingLayer.value = true;
  try {
    await store.dispatch('geodata/deleteProjectLayer', layerToDelete.value.id);
    deleteLayerDialog.value = false;
    layerToDelete.value = null;
  } catch (err) {
    console.error('Failed to delete layer:', err);
  } finally {
    deletingLayer.value = false;
  }
};

// --- Drag and Drop ---
const onDragStart = (event: DragEvent, item: any, itemType: 'folder' | 'object') => {
  if (event.dataTransfer) {
    event.dataTransfer.setData('itemId', item.id);
    event.dataTransfer.setData('itemType', itemType);
    if (itemType === 'object') {
      event.dataTransfer.setData('objectType', item.type);
    }
    event.dataTransfer.effectAllowed = 'move';
    if (event.currentTarget) {
      event.dataTransfer.setDragImage(event.currentTarget as Element, 20, 20);
    }
  }
};

const onDropToRoot = async (event: DragEvent) => {
  const itemId = event.dataTransfer?.getData('itemId');
  const itemType = event.dataTransfer?.getData('itemType');
  const objectType = event.dataTransfer?.getData('objectType');

  if (!itemId) return;

  if (itemType === 'folder') {
    // Move folder to root
    const folder = folders.value.find(f => f.id === itemId);
    if (folder && folder.parentId !== null) {
      await store.dispatch('geodata/updateFolder', { ...folder, parentId: null });
    }
  } else if (itemType === 'object') {
    // Move object to root
    await store.dispatch('geodata/updateFeature', {
      id: itemId,
      type: objectType,
      data: { folderId: null }
    });
  }
};

</script>

<style scoped>
.draggable-item {
  cursor: grab;
}
.draggable-item:active {
  cursor: grabbing;
}
</style>
