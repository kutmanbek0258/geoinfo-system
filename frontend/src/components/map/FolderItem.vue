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
        draggable="true"
        @dragstart="onDragStart($event, folder, 'folder')"
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
import { ref, computed } from 'vue';
import { useStore } from 'vuex';
import type { GeoFolder } from '@/types/api';
import FolderEditDialog from './FolderEditDialog.vue';

const props = defineProps<{
  folder: GeoFolder;
  allFolders: GeoFolder[];
  objects: any[];
}>();

const store = useStore();

const subFolders = computed(() => props.allFolders.filter((f: GeoFolder) => f.parentId === props.folder.id));
const folderObjects = computed(() => props.objects.filter((obj: any) => obj.folderId === props.folder.id));
const selectedFeatureId = computed(() => store.state.geodata.selectedFeatureId);

const isFolderVisible = computed(() => props.folder.characteristics?.visible !== false);

const getIcon = (type: string) => {
  if (type === 'Point') return 'mdi-map-marker';
  if (type === 'MultiLineString') return 'mdi-vector-polyline';
  return 'mdi-vector-polygon';
};

const selectObject = (obj: any) => {
  store.dispatch('geodata/selectFeature', obj.id);
};

const isVisible = (obj: any) => {
  return obj.characteristics?.visible !== false;
};

const toggleVisibility = (obj: any) => {
  const visible = !isVisible(obj);
  const characteristics = { ...obj.characteristics, visible };
  store.dispatch('geodata/updateFeature', {
    id: obj.id,
    type: obj.type,
    data: { characteristics }
  });
};

const toggleFolderVisibility = async () => {
  const visible = !isFolderVisible.value;
  const characteristics = { ...props.folder.characteristics, visible };
  
  await store.dispatch('geodata/updateFolder', {
    ...props.folder,
    characteristics
  });
  
  updateChildrenVisibility(props.folder.id, visible);
};

const updateChildrenVisibility = (folderId: string, visible: boolean) => {
    props.objects.filter((obj: any) => obj.folderId === folderId).forEach((obj: any) => {
        if (isVisible(obj) !== visible) {
            toggleVisibility(obj);
        }
    });
    
    props.allFolders.filter((f: GeoFolder) => f.parentId === folderId).forEach((sf: GeoFolder) => {
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
    await store.dispatch('geodata/updateFeature', {
      id: itemId,
      type: objectType,
      data: { folderId: props.folder.id }
    });
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
