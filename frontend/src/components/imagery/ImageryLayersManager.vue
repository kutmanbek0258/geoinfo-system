<template>
  <v-card>
    <v-toolbar color="secondary" dark>
      <v-toolbar-title>Слои растровых данных</v-toolbar-title>
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
        <v-list-item-subtitle class="font-weight-medium">
          COG Key: {{ layer.cogObjectKey || 'Файл не загружен' }}
        </v-list-item-subtitle>
        <v-list-item-subtitle>
          Дата: {{ new Date(layer.dateCaptured).toLocaleDateString() }} | CRS: {{ layer.crs }}
        </v-list-item-subtitle>

        <template v-slot:append>
          <v-btn icon="mdi-pencil" variant="text" @click="openEditDialog(layer)"></v-btn>
          <v-btn icon="mdi-delete" variant="text" color="error" @click="deleteLayer(layer.id)"></v-btn>
        </template>
      </v-list-item>
    </v-list>

    <div class="text-center pa-4" v-if="totalPages > 1">
        <v-pagination
            v-model="currentPage"
            :length="totalPages"
            rounded="circle"
        ></v-pagination>
    </div>

    <!-- Create/Edit Layer Dialog -->
    <v-dialog v-model="dialog" max-width="600px">
      <v-card>
        <v-card-title class="bg-secondary text-white">
          <span class="headline">{{ isEditing ? 'Редактировать слой' : 'Создать слой' }}</span>
        </v-card-title>
        <v-card-text class="pt-4">
          <v-form ref="form">
            <v-text-field v-model="editableLayer.name" label="Отображаемое имя" variant="outlined" density="comfortable" :rules="[v => !!v || 'Имя обязательно']"></v-text-field>
            <v-textarea v-model="editableLayer.description" label="Описание" variant="outlined" density="comfortable"></v-textarea>
            
            <v-row>
                <v-col cols="12" sm="6">
                    <v-text-field v-model="editableLayer.dateCaptured" label="Дата создания" variant="outlined" density="comfortable" type="date"></v-text-field>
                </v-col>
                <v-col cols="12" sm="6">
                    <v-select v-model="editableLayer.status" :items="['COMPLETED', 'IN_PROCESS', 'REJECTED']" label="Статус" variant="outlined" density="comfortable" :rules="[v => !!v || 'Статус обязателен']"></v-select>
                </v-col>
            </v-row>
            <v-row>
                <v-col cols="12">
                    <v-checkbox
                        v-model="useTiTilerColormap"
                        label="Использовать встроенную шкалу TiTiler"
                        density="comfortable"
                        hide-details
                        class="mb-3"
                    ></v-checkbox>
                </v-col>
            </v-row>
            <v-row v-if="useTiTilerColormap" align="center">
                <v-col cols="12">
                    <v-select
                        v-model="editableLayer.colormapId"
                        :items="titilerColormaps"
                        label="Встроенная шкала TiTiler"
                        variant="outlined"
                        density="comfortable"
                    ></v-select>
                </v-col>
            </v-row>
            <v-row v-else align="center">
                <v-col cols="10">
                    <v-select
                        v-model="selectedStyleObject"
                        :items="availableStyles"
                        item-title="title"
                        item-value="id"
                        return-object
                        label="Цветовая шкала"
                        variant="outlined"
                        density="comfortable"
                    ></v-select>
                </v-col>
                <v-col cols="2" class="text-right">
                    <v-btn icon="mdi-palette" color="primary" @click="showStyleEditor = true" title="Редактор стилей"></v-btn>
                </v-col>
            </v-row>
            <v-row align="center">
                <v-col cols="12">
                    <v-select
                        v-model="editableLayer.resampling"
                        :items="['nearest', 'bilinear', 'cubic', 'cubic_spline', 'lanczos', 'average', 'mode']"
                        label="Метод resampling (пересчет)"
                        variant="outlined"
                        density="comfortable"
                    ></v-select>
                </v-col>
            </v-row>
            <v-row>
                <v-col cols="12">
                    <v-text-field v-model="editableLayer.crs" label="Проекция (CRS, e.g., EPSG:4326)" variant="outlined" density="comfortable" :rules="[v => !!v || 'CRS обязателен']"></v-text-field>
                </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-card-actions class="pa-4">
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="dialog = false">Отмена</v-btn>
          <v-btn color="secondary" variant="elevated" @click="saveLayer">Сохранить</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Raster Style Editor Dialog Component -->
    <RasterStyleEditorDialog v-model="showStyleEditor" @styles-updated="fetchStyles" />
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { ImageryLayer, RasterStyle } from '@/types/api';
import RasterStyleService from '@/services/raster-style.service';
import RasterStyleEditorDialog from './RasterStyleEditorDialog.vue';

const store = useStore();

const dialog = ref(false);
const showStyleEditor = ref(false);
const isEditing = ref(false);
const editableLayer = ref<Partial<ImageryLayer>>({});
const form = ref<any>(null);
const currentPage = ref(1);
const pageSize = ref(10);
const availableStyles = ref<RasterStyle[]>([]);
const selectedStyleObject = ref<RasterStyle | null>(null);

const useTiTilerColormap = ref(false);
const titilerColormaps = ref<string[]>([]);

const isLoading = computed(() => store.state.geodata.isLoading);
const imageryLayers = computed<ImageryLayer[]>(() => store.state.geodata.imageryLayers?.content || []);
const totalPages = computed(() => store.state.geodata.imageryLayers?.totalPages || 0);

const fetchCurrentPage = () => {
    store.dispatch('geodata/fetchImageryLayers', { page: currentPage.value - 1, size: pageSize.value });
}

const fetchStyles = async () => {
    try {
        const response = await RasterStyleService.getRasterStyles(0, 100);
        availableStyles.value = response.data.content;
    } catch (e) {
        console.error("Failed to fetch styles", e);
    }
}

const fetchTiTilerColormaps = async () => {
    try {
        titilerColormaps.value = await RasterStyleService.getTiTilerColorMaps();
    } catch (e) {
        console.error("Failed to fetch TiTiler colorMaps", e);
        titilerColormaps.value = ['viridis', 'magma', 'inferno', 'plasma', 'cividis', 'terrain', 'rdylgn', 'spectral'];
    }
}

onMounted(async () => {
  fetchCurrentPage();
  await fetchStyles();
  await fetchTiTilerColormaps();
});

watch(currentPage, () => {
    fetchCurrentPage();
});

const openCreateDialog = () => {
  isEditing.value = false;
  editableLayer.value = {
    name: '',
    description: '',
    dateCaptured: new Date().toISOString().split('T')[0],
    status: 'COMPLETED',
    crs: 'EPSG:4326',
    colormapId: null,
    resampling: 'nearest'
  };
  useTiTilerColormap.value = false;
  selectedStyleObject.value = availableStyles.value.find(s => s.name === 'raster') || null;
  dialog.value = true;
};

const openEditDialog = (layer: ImageryLayer) => {
  isEditing.value = true;
  editableLayer.value = { ...layer, dateCaptured: layer.dateCaptured ? layer.dateCaptured.split('T')[0] : new Date().toISOString().split('T')[0] };
  useTiTilerColormap.value = !!layer.colormapId;
  selectedStyleObject.value = layer.style || null;
  dialog.value = true;
};

const saveLayer = async () => {
  const { valid } = await form.value.validate();
  if (!valid) return;

  if (useTiTilerColormap.value) {
    editableLayer.value.style = undefined;
  } else {
    editableLayer.value.colormapId = null;
    editableLayer.value.style = selectedStyleObject.value || undefined;
  }

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
  if (confirm('Вы уверены, что хотите удалить этот растровый слой?')) {
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
