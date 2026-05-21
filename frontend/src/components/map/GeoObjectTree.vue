<template>
  <v-list density="compact" nav class="pa-0">
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
  </v-list>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useStore } from 'vuex';
import type { GeoFolder, ProjectPoint, ProjectMultiline, ProjectPolygon } from '@/types/api';
import FolderItem from './FolderItem.vue';

const store = useStore();

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
</script>
