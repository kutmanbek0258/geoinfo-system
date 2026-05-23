<template>
  <v-list density="compact" nav class="pa-0" @dragover.prevent @drop="onDropToRoot">
    <!-- Header with Actions -->
    <v-list-item class="px-2">
      <template v-slot:prepend>
        <span class="text-subtitle-2 font-weight-bold">Слои и объекты</span>
      </template>
      <template v-slot:append>
        <v-btn
          icon="mdi-folder-plus"
          variant="text"
          density="compact"
          color="primary"
          title="Создать папку"
          @click="openCreateFolderDialog"
        ></v-btn>
      </template>
    </v-list-item>
    <v-divider></v-divider>

    <!-- Root Level Folders -->
    <template v-for="folder in rootFolders" :key="folder.id">
      <FolderItem :folder="folder" :all-folders="folders" :objects="allObjects" />
    </template>

    <!-- Root Level Objects (no folder) -->
    <template v-for="obj in rootObjects" :key="obj.id">
      <v-list-item
        @click="selectObject(obj)"
        :active="selectedFeatureId === obj.id"
        :prepend-icon="getIcon(obj.type)"
        :title="obj.name"
        :subtitle="obj.status"
        draggable="true"
        @dragstart="onDragStart($event, obj, 'object')"
        class="draggable-item"
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
    
    <!-- Empty space drop zone indicator -->
    <v-list-item v-if="rootFolders.length === 0 && rootObjects.length === 0" class="text-center text-grey-darken-1 text-caption py-4">
        Перетащите сюда объекты для перемещения в корень
    </v-list-item>
  </v-list>

  <FolderEditDialog
    v-model="folderDialog"
    :project-id="projectId"
    :parent-id="null"
    @saved="onFolderSaved"
  />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useStore } from 'vuex';
import type { GeoFolder, ProjectPoint, ProjectMultiline, ProjectPolygon } from '@/types/api';
import FolderItem from './FolderItem.vue';
import FolderEditDialog from './FolderEditDialog.vue';

const store = useStore();

const projectId = computed(() => store.state.geodata.selectedProjectId);
const folders = computed<GeoFolder[]>(() => store.state.geodata.folders);
const points = computed(() => store.state.geodata.points.map((p: ProjectPoint) => ({ ...p, type: 'Point' })));
const multilines = computed(() => store.state.geodata.multilines.map((m: ProjectMultiline) => ({ ...m, type: 'MultiLineString' })));
const polygons = computed(() => store.state.geodata.polygons.map((p: ProjectPolygon) => ({ ...p, type: 'Polygon' })));

const allObjects = computed(() => [...points.value, ...multilines.value, ...polygons.value]);
const selectedFeatureId = computed(() => store.state.geodata.selectedFeatureId);

const rootFolders = computed(() => folders.value.filter((f: GeoFolder) => !f.parentId));
const rootObjects = computed(() => allObjects.value.filter((obj: any) => !obj.folderId));

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

// --- Folder Management ---
const folderDialog = ref(false);

const openCreateFolderDialog = () => {
  folderDialog.value = true;
};

const onFolderSaved = () => {
  // Extra logic can be added here
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
