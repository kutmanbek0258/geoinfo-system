<template>
  <v-card>
    <v-toolbar color="secondary" dark>
      <v-toolbar-title>Imagery Layers</v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn icon @click="openCreateDialog">
        <v-icon>mdi-plus</v-icon>
      </v-btn>
    </v-toolbar>

    <v-progress-linear :active="isLoading" indeterminate color="secondary"></v-progress-linear>

    <v-list lines="three">
      <v-list-item
        v-for="layer in imageryLayers"
        :key="layer.id"
      >
        <v-list-item-title>{{ layer.name }}</v-list-item-title>
        <v-list-item-subtitle>
          Workspace: {{ layer.workspace }}, Layer: {{ layer.layerName }}
        </v-list-item-subtitle>
        <v-list-item-subtitle>
          Date: {{ layer.dateCaptured }}
        </v-list-item-subtitle>

        <template v-slot:append>
          <v-btn icon="mdi-pencil" variant="text" @click="openEditDialog(layer)"></v-btn>
          <v-btn icon="mdi-delete" variant="text" color="error" @click="deleteLayer(layer.id)"></v-btn>
        </template>
      </v-list-item>
    </v-list>

    <div class="text-center">
        <v-pagination
            v-model="currentPage"
            :length="totalPages"
            rounded="circle"
        ></v-pagination>
    </div>

    <v-dialog v-model="dialog" max-width="800px">
      <v-card>
        <v-card-title>
          <span class="headline">{{ isEditing ? 'Edit Layer' : 'New Layer' }}</span>
        </v-card-title>
        <v-card-text>
          <v-form ref="form">
            <v-text-field v-model="editableLayer.name" label="Display Name" :rules="[v => !!v || 'Name is required']"></v-text-field>
            <v-textarea v-model="editableLayer.description" label="Description"></v-textarea>
            <v-row>
              <v-col cols="12" sm="6">
                <v-text-field v-model="editableLayer.workspace" label="GeoServer Workspace" :rules="[v => !!v || 'Workspace is required']"></v-text-field>
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model="editableLayer.layerName" label="GeoServer Layer Name" :rules="[v => !!v || 'Layer name is required']"></v-text-field>
              </v-col>
            </v-row>
            <v-text-field v-model="editableLayer.serviceUrl" label="Service URL (WMS/WMTS)" :rules="[v => !!v || 'URL is required']"></v-text-field>
            <v-row>
                <v-col cols="12" sm="6">
                    <v-text-field v-model="editableLayer.dateCaptured" label="Date Captured" type="date"></v-text-field>
                </v-col>
                <v-col cols="12" sm="6">
                    <v-select v-model="editableLayer.status" :items="['COMPLETED', 'IN_PROCESS', 'REJECTED']" label="Status" :rules="[v => !!v || 'Status is required']"></v-select>
                </v-col>
            </v-row>
            <v-row>
                <v-col cols="12" sm="6">
                    <v-text-field v-model="editableLayer.style" label="Style"></v-text-field>
                </v-col>
                <v-col cols="12" sm="6">
                    <v-text-field v-model="editableLayer.crs" label="CRS (e.g., EPSG:4326)" :rules="[v => !!v || 'CRS is required']"></v-text-field>
                </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="blue darken-1" text @click="dialog = false">Cancel</v-btn>
          <v-btn color="blue darken-1" text @click="saveLayer">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { ImageryLayer } from '@/types/api';

// Используем Vuex store
const store = useStore();

// --- Состояние компонента ---
const dialog = ref(false);
const isEditing = ref(false);
const editableLayer = ref<Partial<ImageryLayer>>({});
const form = ref<any>(null);
const currentPage = ref(1);
const pageSize = ref(10);

// --- Получение данных из Vuex ---
const isLoading = computed(() => store.state.geodata.isLoading);
const imageryLayers = computed<ImageryLayer[]>(() => store.state.geodata.imageryLayers?.content || []);
const totalPages = computed(() => store.state.geodata.imageryLayers?.totalPages || 0);

// --- Методы для загрузки данных ---
const fetchCurrentPage = () => {
    store.dispatch('geodata/fetchImageryLayers', { page: currentPage.value - 1, size: pageSize.value });
}

// --- Жизненный цикл и наблюдатели ---
onMounted(() => {
  fetchCurrentPage();
});

watch(currentPage, () => {
    fetchCurrentPage();
});

// --- Методы ---
const openCreateDialog = () => {
  isEditing.value = false;
  editableLayer.value = {
    name: '',
    description: '',
    workspace: 'geoinfo',
    layerName: '',
    serviceUrl: 'http://localhost:8080/geoserver/wms',
    dateCaptured: new Date().toISOString().split('T')[0],
    status: 'COMPLETED',
    style: '',
    crs: 'EPSG:4326'
  };
  dialog.value = true;
};

const openEditDialog = (layer: ImageryLayer) => {
  isEditing.value = true;
  editableLayer.value = { ...layer, dateCaptured: layer.dateCaptured.split('T')[0] };
  dialog.value = true;
};

const saveLayer = async () => {
  const { valid } = await form.value.validate();
  if (!valid) return;

  const actionPayload = {
      layerData: editableLayer.value,
      page: currentPage.value - 1,
      size: pageSize.value
  };

  if (isEditing.value) {
    await store.dispatch('geodata/updateImageryLayer', actionPayload);
  } else {
    await store.dispatch('geodata/createImageryLayer', actionPayload);
  }
  
  dialog.value = false;
};

const deleteLayer = async (id: string) => {
  if (confirm('Are you sure you want to delete this imagery layer?')) {
    const actionPayload = { 
        layerId: id, 
        page: currentPage.value - 1, 
        size: pageSize.value 
    };
    await store.dispatch('geodata/deleteImageryLayer', actionPayload);
  }
};
</script>

<style scoped>
.headline {
  font-weight: 500;
}
</style>
