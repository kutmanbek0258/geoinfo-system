<template>
  <v-dialog v-model="dialog" max-width="850px" persistent>
    <v-card>
      <v-toolbar color="primary" dark>
        <v-toolbar-title>
          <v-icon class="mr-2">mdi-palette</v-icon>
          Редактор стилей интерполяции
        </v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn icon @click="close">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-toolbar>

      <v-card-text class="pa-4">
        <v-row>
          <!-- Left Panel: Style list -->
          <v-col cols="12" md="4" class="border-right pr-4">
            <div class="d-flex align-center justify-space-between mb-4">
              <span class="text-subtitle-1 font-weight-bold">Доступные шкалы</span>
              <v-btn density="comfortable" icon="mdi-plus" color="success" @click="initNewStyle" title="Создать новый стиль"></v-btn>
            </div>
            <v-divider class="mb-2"></v-divider>

            <v-list density="compact" nav class="style-list overflow-y-auto" style="max-height: 50vh;">
              <v-list-item
                v-for="s in styles"
                :key="s.id"
                :active="selectedStyle?.id === s.id"
                color="primary"
                @click="selectStyle(s)"
              >
                <template v-slot:prepend>
                  <v-icon :color="s.isSystem ? 'amber-darken-2' : 'blue'">
                    {{ s.isSystem ? 'mdi-shield-check' : 'mdi-account-circle' }}
                  </v-icon>
                </template>
                <v-list-item-title class="font-weight-medium">{{ s.title }}</v-list-item-title>
                <v-list-item-subtitle>{{ s.name }}</v-list-item-subtitle>
              </v-list-item>
            </v-list>
          </v-col>

          <!-- Right Panel: Editor Form -->
          <v-col cols="12" md="8" class="pl-4">
            <v-form v-if="selectedStyle" ref="styleForm">
              <v-row density="compact">
                <v-col cols="12" sm="6">
                  <v-text-field
                    v-model="selectedStyle.title"
                    label="Название шкалы"
                    variant="outlined"
                    density="comfortable"
                    :rules="[v => !!v || 'Название обязательно']"
                  ></v-text-field>
                </v-col>
                <v-col cols="12" sm="6">
                  <v-text-field
                    v-model="selectedStyle.name"
                    label="Системный код (Name)"
                    variant="outlined"
                    density="comfortable"
                    :disabled="selectedStyle.isSystem || !!selectedStyle.id"
                    :rules="[v => !!v || 'Код обязателен', v => /^[a-z0-9_-]+$/i.test(v) || 'Только латиница, цифры и _ -']"
                  ></v-text-field>
                </v-col>
              </v-row>

              <div class="d-flex align-center justify-space-between mt-2 mb-4">
                <span class="text-subtitle-2 font-weight-bold">Точки градиента</span>
                <v-btn size="small" prepend-icon="mdi-plus" color="primary" @click="addEntry">Добавить точку</v-btn>
              </div>

              <!-- Color Ramp Table -->
              <div class="entries-container overflow-y-auto" style="max-height: 38vh;">
                <v-row
                  v-for="(entry, index) in selectedStyle.config"
                  :key="index"
                  align="center"
                  density="compact"
                  class="mb-2 entry-row pa-2 border rounded"
                >
                  <!-- Color Preview with Menu Picker -->
                  <v-col cols="2" class="d-flex justify-center">
                    <v-menu :close-on-content-click="false" location="bottom start">
                      <template v-slot:activator="{ props }">
                        <div
                          v-bind="props"
                          class="color-preview rounded elevation-2 cursor-pointer"
                          :style="{ backgroundColor: entry.color }"
                        ></div>
                      </template>
                      <v-card class="pa-2">
                        <v-color-picker v-model="entry.color" hide-inputs show-swatches></v-color-picker>
                      </v-card>
                    </v-menu>
                  </v-col>

                  <!-- Quantity Value -->
                  <v-col cols="3">
                    <v-text-field
                      v-model.number="entry.quantity"
                      label="Значение"
                      type="number"
                      step="0.01"
                      variant="underlined"
                      density="compact"
                      hide-details
                    ></v-text-field>
                  </v-col>

                  <!-- Opacity Value -->
                  <v-col cols="2">
                    <v-text-field
                      v-model.number="entry.opacity"
                      label="Alpha"
                      type="number"
                      min="0"
                      max="1"
                      step="0.1"
                      variant="underlined"
                      density="compact"
                      hide-details
                    ></v-text-field>
                  </v-col>

                  <!-- Label -->
                  <v-col cols="4">
                    <v-text-field
                      v-model="entry.label"
                      label="Описание"
                      variant="underlined"
                      density="compact"
                      hide-details
                    ></v-text-field>
                  </v-col>

                  <!-- Delete point -->
                  <v-col cols="1" class="text-right">
                    <v-btn
                      density="comfortable"
                      icon="mdi-trash-can"
                      variant="text"
                      color="error"
                      @click="removeEntry(index)"
                      :disabled="selectedStyle.config.length <= 1"
                    ></v-btn>
                  </v-col>
                </v-row>
              </div>

              <!-- Action buttons for selected style -->
              <v-divider class="my-4"></v-divider>
              <div class="d-flex justify-space-between">
                <v-btn
                  v-if="selectedStyle.id && !selectedStyle.isSystem"
                  prepend-icon="mdi-delete"
                  color="error"
                  variant="outlined"
                  @click="deleteStyle"
                >
                  Удалить шкалу
                </v-btn>
                <div v-else></div>
                
                <v-btn color="success" prepend-icon="mdi-content-save" @click="saveStyle">
                  Сохранить шкалу
                </v-btn>
              </div>
            </v-form>

            <div v-else class="d-flex flex-column align-center justify-center fill-height" style="min-height: 40vh;">
              <v-icon size="64" color="grey-lighten-1">mdi-palette-swatch-outline</v-icon>
              <span class="text-grey-darken-1 mt-2">Выберите или создайте цветовую шкалу</span>
            </div>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { RasterStyle, ColorMapEntry } from '@/types/api';
import RasterStyleService from '@/services/raster-style.service';

const props = defineProps({
  modelValue: Boolean
});

const emit = defineEmits(['update:modelValue', 'styles-updated']);

const dialog = ref(false);
const styles = ref<RasterStyle[]>([]);
const selectedStyle = ref<RasterStyle | null>(null);
const styleForm = ref<any>(null);

watch(() => props.modelValue, async (val) => {
  dialog.value = val;
  if (val) {
    await fetchStyles();
  }
});

watch(dialog, (val) => {
  emit('update:modelValue', val);
});

const fetchStyles = async () => {
  try {
    const response = await RasterStyleService.getRasterStyles(0, 50); // Get first 50 styles
    styles.value = response.data.content;
    if (styles.value.length > 0) {
      // Keep selected style if it exists, or select the first one
      const found = styles.value.find(s => s.id === selectedStyle.value?.id);
      if (found) {
        selectStyle(found);
      } else {
        selectStyle(styles.value[0]);
      }
    }
  } catch (e) {
    console.error("Failed to fetch raster styles", e);
  }
};

const selectStyle = (style: RasterStyle) => {
  // Deep clone to avoid editing directly in the list before saving
  selectedStyle.value = JSON.parse(JSON.stringify(style));
};

const initNewStyle = () => {
  selectedStyle.value = {
    id: '',
    name: '',
    title: 'Новая цветовая шкала',
    type: 'ramp',
    config: [
      { color: '#FF0000', quantity: 0.0, opacity: 1.0, label: 'Мин' },
      { color: '#00FF00', quantity: 1.0, opacity: 1.0, label: 'Макс' }
    ],
    isSystem: false
  };
};

const addEntry = () => {
  if (!selectedStyle.value) return;
  const config = selectedStyle.value.config;
  const lastVal = config.length > 0 ? config[config.length - 1].quantity : 0;
  config.push({
    color: '#3F51B5',
    quantity: lastVal + 0.1,
    opacity: 1.0,
    label: `Точка ${config.length + 1}`
  });
  // Sort entries to keep order visual
  sortEntries();
};

const removeEntry = (index: number) => {
  if (!selectedStyle.value) return;
  selectedStyle.value.config.splice(index, 1);
};

const sortEntries = () => {
  if (!selectedStyle.value) return;
  selectedStyle.value.config.sort((a, b) => a.quantity - b.quantity);
};

const close = () => {
  dialog.value = false;
  selectedStyle.value = null;
};

const saveStyle = async () => {
  if (!selectedStyle.value) return;
  const { valid } = await styleForm.value.validate();
  if (!valid) return;

  sortEntries();

  try {
    if (selectedStyle.value.id) {
      await RasterStyleService.updateRasterStyle(selectedStyle.value.id, selectedStyle.value);
    } else {
      await RasterStyleService.createRasterStyle(selectedStyle.value);
    }
    await fetchStyles();
    emit('styles-updated');
  } catch (e) {
    console.error("Failed to save style", e);
  }
};

const deleteStyle = async () => {
  if (!selectedStyle.value || !selectedStyle.value.id) return;
  if (confirm(`Вы уверены, что хотите удалить шкалу "${selectedStyle.value.title}"?`)) {
    try {
      await RasterStyleService.deleteRasterStyle(selectedStyle.value.id);
      selectedStyle.value = null;
      await fetchStyles();
      emit('styles-updated');
    } catch (e) {
      console.error("Failed to delete style", e);
    }
  }
};
</script>

<style scoped>
.border-right {
  border-right: 1px solid rgba(0, 0, 0, 0.12);
}

.style-list {
  padding: 0;
}

.color-preview {
  width: 32px;
  height: 32px;
  border: 2px solid white;
  box-shadow: 0 0 4px rgba(0,0,0,0.3);
}

.entry-row {
  transition: background-color 0.2s;
}

.entry-row:hover {
  background-color: rgba(0, 0, 0, 0.02);
}
</style>
