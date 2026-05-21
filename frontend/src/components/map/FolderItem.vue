<template>
  <v-list-group :value="folder.id">
    <template v-slot:activator="{ props }">
      <v-list-item
        v-bind="props"
        prepend-icon="mdi-folder"
        :title="folder.name"
      >
        <template v-slot:append>
          <v-btn
            icon="mdi-eye"
            variant="text"
            density="compact"
            :color="isFolderVisible ? 'primary' : 'grey'"
            @click.stop="toggleFolderVisibility"
          ></v-btn>
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
        class="ml-4"
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
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useStore } from 'vuex';
import type { GeoFolder } from '@/types/api';

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
  
  // 1. Update folder state
  await store.dispatch('geodata/updateFolder', {
    ...props.folder,
    characteristics
  });
  
  // 2. Recursively update all objects in this folder and subfolders (Batch update would be better, but doing it sequentially for now)
  // Note: For large projects, this should be a single backend call.
  updateChildrenVisibility(props.folder.id, visible);
};

const updateChildrenVisibility = (folderId: string, visible: boolean) => {
    // Update objects in this folder
    props.objects.filter((obj: any) => obj.folderId === folderId).forEach((obj: any) => {
        if (isVisible(obj) !== visible) {
            toggleVisibility(obj);
        }
    });
    
    // Recurse into subfolders
    props.allFolders.filter((f: GeoFolder) => f.parentId === folderId).forEach((sf: GeoFolder) => {
        updateChildrenVisibility(sf.id, visible);
    });
};

</script>

<style scoped>
.ml-4 {
  margin-left: 16px !important;
}
</style>
