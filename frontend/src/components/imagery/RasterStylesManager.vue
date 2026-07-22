<template>
  <v-card>
    <v-toolbar color="secondary" dark>
      <v-toolbar-title>
        <v-icon class="mr-2">mdi-palette</v-icon>
        Цветовые шкалы интерполяции
      </v-toolbar-title>
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

          <v-list density="compact" nav class="style-list overflow-y-auto" style="max-height: 60vh;">
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

            <!-- Gradient Auto-Generator Block -->
            <v-expansion-panels class="mb-4">
              <v-expansion-panel>
                <v-expansion-panel-title class="font-weight-bold text-subtitle-2">
                  <v-icon class="mr-2" color="primary">mdi-auto-fix</v-icon>
                  Автоматическая генерация градиента
                </v-expansion-panel-title>
                <v-expansion-panel-text class="pt-2">
                  <v-row density="compact">
                    <v-col cols="4">
                      <v-text-field
                        v-model.number="genStartValue"
                        label="Начальное значение"
                        type="number"
                        variant="outlined"
                        density="compact"
                        hide-details
                      ></v-text-field>
                    </v-col>
                    <v-col cols="4">
                      <v-text-field
                        v-model.number="genEndValue"
                        label="Конечное значение"
                        type="number"
                        variant="outlined"
                        density="compact"
                        hide-details
                      ></v-text-field>
                    </v-col>
                    <v-col cols="4">
                      <v-text-field
                        v-model.number="genIntervals"
                        label="Кол-во диапазонов"
                        type="number"
                        min="1"
                        max="50"
                        variant="outlined"
                        density="compact"
                        hide-details
                      ></v-text-field>
                    </v-col>
                  </v-row>

                  <v-row density="compact" class="mt-2" align="center">
                    <v-col cols="6" class="d-flex align-center justify-space-around">
                      <div class="d-flex flex-column align-center">
                        <span class="text-caption mb-1">Начальный цвет</span>
                        <v-menu :close-on-content-click="false" location="bottom start">
                          <template v-slot:activator="{ props }">
                            <div
                              v-bind="props"
                              class="color-preview rounded elevation-2 cursor-pointer"
                              :style="{ backgroundColor: genStartColor, width: '48px', height: '32px' }"
                            ></div>
                          </template>
                          <v-card class="pa-2">
                            <v-color-picker v-model="genStartColor" hide-inputs show-swatches></v-color-picker>
                          </v-card>
                        </v-menu>
                      </div>
                      <div class="d-flex flex-column align-center">
                        <span class="text-caption mb-1">Конечный цвет</span>
                        <v-menu :close-on-content-click="false" location="bottom start">
                          <template v-slot:activator="{ props }">
                            <div
                              v-bind="props"
                              class="color-preview rounded elevation-2 cursor-pointer"
                              :style="{ backgroundColor: genEndColor, width: '48px', height: '32px' }"
                            ></div>
                          </template>
                          <v-card class="pa-2">
                            <v-color-picker v-model="genEndColor" hide-inputs show-swatches></v-color-picker>
                          </v-card>
                        </v-menu>
                      </div>
                    </v-col>
                    <v-col cols="6" class="text-right">
                      <v-btn
                        color="primary"
                        prepend-icon="mdi-creation"
                        @click="generateRamp"
                        :disabled="genIntervals < 1"
                      >
                        Сгенерировать
                      </v-btn>
                    </v-col>
                  </v-row>
                </v-expansion-panel-text>
              </v-expansion-panel>
            </v-expansion-panels>

            <div class="d-flex align-center justify-space-between mt-2 mb-4">
              <span class="text-subtitle-2 font-weight-bold">Точки градиента</span>
              <v-btn size="small" prepend-icon="mdi-plus" color="primary" @click="addEntry">Добавить точку</v-btn>
            </div>

            <!-- Color Ramp Table -->
            <div class="entries-container overflow-y-auto pr-2" style="max-height: 45vh;">
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

          <div v-else class="d-flex flex-column align-center justify-center fill-height" style="min-height: 50vh;">
            <v-icon size="64" color="grey-lighten-1">mdi-palette-swatch-outline</v-icon>
            <span class="text-grey-darken-1 mt-2">Выберите или создайте цветовую шкалу</span>
          </div>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { RasterStyle } from '@/types/api';
import RasterStyleService from '@/services/raster-style.service';

const styles = ref<RasterStyle[]>([]);
const selectedStyle = ref<RasterStyle | null>(null);
const styleForm = ref<any>(null);

// Gradient Auto-Generator State
const genStartValue = ref<number>(150);
const genEndValue = ref<number>(550);
const genIntervals = ref<number>(10);
const genStartColor = ref<string>('#4CAF50');
const genEndColor = ref<string>('#2196F3');

const parseHex = (color: string): { r: number, g: number, b: number } => {
  const c = color.startsWith('#') ? color.slice(1) : color;
  if (c.length === 3) {
    return {
      r: parseInt(c[0] + c[0], 16),
      g: parseInt(c[1] + c[1], 16),
      b: parseInt(c[2] + c[2], 16)
    };
  }
  return {
    r: parseInt(c.slice(0, 2), 16) || 0,
    g: parseInt(c.slice(2, 4), 16) || 0,
    b: parseInt(c.slice(4, 6), 16) || 0
  };
};

const rgbToHex = (r: number, g: number, b: number): string => {
  return '#' + [r, g, b].map(x => {
    const hex = Math.max(0, Math.min(255, x)).toString(16);
    return hex.length === 1 ? '0' + hex : hex;
  }).join('').toUpperCase();
};

const interpolateColor = (color1: string, color2: string, factor: number): string => {
  const c1 = parseHex(color1);
  const c2 = parseHex(color2);
  const r = Math.round(c1.r + factor * (c2.r - c1.r));
  const g = Math.round(c1.g + factor * (c2.g - c1.g));
  const b = Math.round(c1.b + factor * (c2.b - c1.b));
  return rgbToHex(r, g, b);
};

const generateRamp = () => {
  if (!selectedStyle.value) return;
  const N = Math.max(1, genIntervals.value);
  const startVal = genStartValue.value;
  const endVal = genEndValue.value;
  const step = (endVal - startVal) / N;

  const config = [];
  for (let i = 0; i <= N; i++) {
    const quantity = parseFloat((startVal + i * step).toFixed(4));
    const factor = i / N;
    const color = interpolateColor(genStartColor.value, genEndColor.value, factor);
    let label = '';
    if (i === 0) label = 'Мин';
    else if (i === N) label = 'Макс';
    else label = `Интервал ${i}`;

    config.push({
      color,
      quantity,
      opacity: 1.0,
      label
    });
  }

  selectedStyle.value.config = config;
};

const fetchStyles = async () => {
  try {
    const response = await RasterStyleService.getRasterStyles(0, 50); // Get first 50 styles
    styles.value = response.data.content;
    if (styles.value.length > 0) {
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
    quantity: lastVal + 1.0,
    opacity: 1.0,
    label: `Точка ${config.length + 1}`
  });
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
    } catch (e) {
      console.error("Failed to delete style", e);
    }
  }
};

onMounted(() => {
  fetchStyles();
});
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
