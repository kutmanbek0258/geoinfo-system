<template>
  <v-list-group 
    :value="folder.id" 
    @dragover.prevent="onDragOver" 
    @dragleave="onDragLeave"
    @drop.stop="onDrop"
    :class="{ 'drop-active': isDragOver }"
  >
    <template v-slot:activator="{ props: vProps }">
      <v-list-item
        v-bind="vProps"
        prepend-icon="mdi-folder"
        :title="folder.name"
        class="folder-item draggable-item"
        :active="selectedFolderId === folder.id"
        draggable="true"
        @dragstart="onDragStart($event, folder, 'folder')"
        @click="selectFolder"
      >
        <template v-slot:append>
          <div class="folder-actions">
            <v-btn
              icon="mdi-folder-plus"
              variant="text"
              density="compact"
              color="primary"
              title="Создать подпапку"
              @click.stop="openCreateSubfolderDialog"
            ></v-btn>
            <v-btn
              icon="mdi-pencil"
              variant="text"
              density="compact"
              color="grey-darken-1"
              title="Редактировать"
              @click.stop="openEditFolderDialog"
            ></v-btn>
            <v-btn
              icon="mdi-eye"
              variant="text"
              density="compact"
              :color="isFolderVisible ? 'primary' : 'grey'"
              title="Переключить видимость"
              @click.stop="toggleFolderVisibility"
            ></v-btn>
            <v-btn
              icon="mdi-delete"
              variant="text"
              density="compact"
              color="error"
              title="Удалить"
              @click.stop="confirmDelete"
            ></v-btn>
          </div>
        </template>
      </v-list-item>
    </template>

    <!-- Sub-folders -->
    <template v-for="subFolder in subFolders" :key="subFolder.id">
      <FolderItem :folder="subFolder" :all-folders="allFolders" :objects="objects" class="ml-4" />
    </template>

    <!-- Objects in this folder -->
    <template v-for="obj in folderObjects" :key="obj.id">
      <v-list-item
        v-if="obj.type !== 'Raster'"
        @click="selectObject(obj)"
        :active="selectedFeatureId === obj.id"
        :prepend-icon="getIcon(obj.type)"
        :title="obj.name"
        :subtitle="obj.status"
        class="ml-4 draggable-item"
        draggable="true"
        @dragstart="onDragStart($event, obj, 'object')"
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

      <!-- Raster item in folder -->
      <div v-else class="pr-2 py-1 border-bottom d-flex flex-column" style="margin-left: 24px;">
        <div class="d-flex align-center justify-space-between draggable-item" draggable="true" @dragstart="onDragStart($event, obj, 'object')">
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
              @click.stop="openStyleEditor"
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

          <div v-if="getUseTiTilerColormap(obj)" class="d-flex align-center mb-2">
            <v-text-field
              :model-value="obj.characteristics?.rescaleMin ?? ''"
              label="Rescale Min"
              type="number"
              density="compact"
              variant="outlined"
              hide-details
              style="font-size: 11px;"
              class="mr-2"
              @update:model-value="val => handleLayerRescaleChange(obj, 'min', val)"
            />
            <v-text-field
              :model-value="obj.characteristics?.rescaleMax ?? ''"
              label="Rescale Max"
              type="number"
              density="compact"
              variant="outlined"
              hide-details
              style="font-size: 11px;"
              @update:model-value="val => handleLayerRescaleChange(obj, 'max', val)"
            />
          </div>

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

  <!-- Subfolder/Edit Dialog -->
  <FolderEditDialog
    v-model="editDialog"
    :project-id="folder.projectId"
    :parent-id="dialogParentId"
    :folder="editableFolder"
  />

  <!-- Delete Confirmation -->
  <v-dialog v-model="deleteDialog" max-width="400px">
    <v-card>
      <v-card-title class="text-h6">Удалить папку?</v-card-title>
      <v-card-text>
        Вы уверены, что хотите удалить папку <strong>{{ folder.name }}</strong>? 
        Все объекты внутри папки будут перемещены в корень проекта.
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="deleteDialog = false">Отмена</v-btn>
        <v-btn color="error" variant="elevated" @click="executeDelete" :loading="deleting">Удалить</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, inject } from 'vue';
import { useStore } from 'vuex';
import type { GeoFolder } from '@/types/api';
import FolderEditDialog from './FolderEditDialog.vue';

const props = defineProps<{
  folder: GeoFolder;
  allFolders: GeoFolder[];
  objects: any[];
}>();

const customStylesSelectItems = inject<any>('customStylesSelectItems');
const titilerColormaps = inject<any>('titilerColormaps');
const getUseTiTilerColormap = inject<any>('getUseTiTilerColormap');
const toggleTiTilerColormap = inject<any>('toggleTiTilerColormap');
const handleLayerCustomStyleChange = inject<any>('handleLayerCustomStyleChange');
const handleLayerColormapChange = inject<any>('handleLayerColormapChange');
const handleLayerResamplingChange = inject<any>('handleLayerResamplingChange');
const handleLayerRescaleChange = inject<any>('handleLayerRescaleChange');
const openStyleEditor = inject<any>('openStyleEditor');
const isRasterVisible = inject<any>('isRasterVisible');
const toggleRasterVisibility = inject<any>('toggleRasterVisibility');
const getRasterOpacity = inject<any>('getRasterOpacity');
const setRasterOpacity = inject<any>('setRasterOpacity');

const store = useStore();

const subFolders = computed(() => props.allFolders.filter((f: GeoFolder) => f.parentId === props.folder.id));
const folderObjects = computed(() => props.objects.filter((obj: any) => obj.folderId === props.folder.id));
const selectedFeatureId = computed(() => store.state.geodata.selectedFeatureId);
const selectedFolderId = computed(() => store.state.geodata.selectedFolderId);

const isFolderVisible = computed(() => props.folder.characteristics?.visible !== false);

const getIcon = (type: string) => {
  if (type === 'Point') return 'mdi-map-marker';
  if (type === 'MultiLineString') return 'mdi-vector-polyline';
  return 'mdi-vector-polygon';
};

const selectObject = (obj: any) => {
  store.commit('geodata/SET_SELECTED_FOLDER_ID', obj.folderId);
  store.dispatch('geodata/selectFeature', { id: obj.id, source: 'list' });
};

const selectFolder = () => {
  store.commit('geodata/SET_SELECTED_FOLDER_ID', props.folder.id);
};

const isVisible = (obj: any) => {
  return obj.characteristics?.visible !== false;
};

const toggleVisibility = (obj: any) => {
  store.dispatch('geodata/toggleFeatureVisibility', { id: obj.id, type: obj.type });
};

const toggleFolderVisibility = async () => {
  const visible = !isFolderVisible.value;
  const characteristics = { ...props.folder.characteristics, visible };
  
  await store.dispatch('geodata/updateFolder', {
    ...props.folder,
    characteristics
  });
  
  // Обновляем видимость дочерних объектов оптимистично (без await)
  updateChildrenVisibility(props.folder.id, visible);
};

const updateChildrenVisibility = (folderId: string, visible: boolean) => {
    props.objects
        .filter((obj: any) => obj.folderId === folderId)
        .forEach((obj: any) => {
            const currentVisible = obj.characteristics?.visible !== false;
            if (currentVisible !== visible) {
                store.dispatch('geodata/toggleFeatureVisibility', { id: obj.id, type: obj.type });
            }
        });
    
    props.allFolders
        .filter((f: GeoFolder) => f.parentId === folderId)
        .forEach((sf: GeoFolder) => {
            updateChildrenVisibility(sf.id, visible);
        });
};

// --- Folder Actions ---
const editDialog = ref(false);
const deleteDialog = ref(false);
const deleting = ref(false);
const editableFolder = ref<GeoFolder | null>(null);
const dialogParentId = ref<string | null>(null);

const openCreateSubfolderDialog = () => {
  editableFolder.value = null;
  dialogParentId.value = props.folder.id;
  editDialog.value = true;
};

const openEditFolderDialog = () => {
  editableFolder.value = props.folder;
  dialogParentId.value = props.folder.parentId || null;
  editDialog.value = true;
};

const confirmDelete = () => {
  deleteDialog.value = true;
};

const executeDelete = async () => {
  deleting.value = true;
  try {
    await store.dispatch('geodata/deleteFolder', props.folder.id);
    deleteDialog.value = false;
  } catch (error) {
    console.error('Failed to delete folder', error);
  } finally {
    deleting.value = false;
  }
};

// --- Drag and Drop ---
const isDragOver = ref(false);

const onDragStart = (event: DragEvent, item: any, itemType: 'folder' | 'object') => {
  if (event.dataTransfer) {
    event.dataTransfer.setData('itemId', item.id);
    event.dataTransfer.setData('itemType', itemType);
    if (itemType === 'object') {
      event.dataTransfer.setData('objectType', item.type);
    }
    event.dataTransfer.effectAllowed = 'move';
    if (event.currentTarget) {
      event.dataTransfer.setDragImage(event.currentTarget as Element, 20, 10);
    }
  }
};

const onDragOver = (event: DragEvent) => {
    isDragOver.value = true;
};

const onDragLeave = () => {
    isDragOver.value = false;
};

const onDrop = async (event: DragEvent) => {
  isDragOver.value = false;
  const itemId = event.dataTransfer?.getData('itemId');
  const itemType = event.dataTransfer?.getData('itemType');
  const objectType = event.dataTransfer?.getData('objectType');

  if (!itemId || itemId === props.folder.id) return;

  if (itemType === 'folder') {
    // 1. Check for circular dependency
    if (isDescendant(itemId, props.folder.id)) {
        console.warn("Circular dependency detected. Cannot move folder into its descendant.");
        return;
    }
    
    // 2. Move folder to this folder
    const targetFolder = props.allFolders.find(f => f.id === itemId);
    if (targetFolder && targetFolder.parentId !== props.folder.id) {
      await store.dispatch('geodata/updateFolder', { ...targetFolder, parentId: props.folder.id });
    }
  } else if (itemType === 'object') {
    // 3. Move object to this folder
    if (objectType === 'Raster') {
      const rasterInfo = store.state.geodata.projectRasters?.content?.find((r: any) => r.id === itemId);
      if (rasterInfo) {
        await store.dispatch('geodata/updateProjectRaster', {
          layerData: { ...rasterInfo, folderId: props.folder.id },
          page: 0, size: 100
        });
      }
    } else {
      await store.dispatch('geodata/updateFeature', {
        id: itemId,
        type: objectType,
        data: { folderId: props.folder.id }
      });
    }
  }
};

const isDescendant = (parentId: string, childId: string): boolean => {
    const child = props.allFolders.find(f => f.id === childId);
    if (!child || !child.parentId) return false;
    if (child.parentId === parentId) return true;
    return isDescendant(parentId, child.parentId);
};

</script>

<style scoped>
.ml-4 {
  margin-left: 16px !important;
}
.folder-actions {
  display: flex;
  opacity: 0.7;
}
.folder-item:hover .folder-actions {
  opacity: 1;
}
.draggable-item {
  cursor: grab;
}
.draggable-item:active {
  cursor: grabbing;
}
.drop-active {
    background-color: rgba(var(--v-theme-primary), 0.1);
    border: 1px dashed rgb(var(--v-theme-primary));
}
</style>
