<template>
  <div class="analysis-panel">
    <!-- Header -->
    <div class="analysis-panel__header" @click="expanded = !expanded">
      <div class="d-flex align-center gap-2">
        <v-icon size="16" color="deep-orange">mdi-flask-outline</v-icon>
        <span class="text-caption font-weight-bold">Задачи анализа</span>
        <v-chip
          v-if="pendingCount > 0"
          size="x-small"
          color="warning"
          class="ml-1"
          :text="String(pendingCount)"
        />
        <v-chip
          v-if="completedCount > 0"
          size="x-small"
          color="success"
          class="ml-1"
          :text="String(completedCount)"
        />
      </div>
      <v-icon size="14" color="grey">{{ expanded ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
    </div>

    <!-- Task list -->
    <v-expand-transition>
      <div v-if="expanded">
        <div v-if="analysisTasks.length === 0" class="analysis-panel__empty">
          <v-icon size="20" color="grey-lighten-1">mdi-flask-empty-outline</v-icon>
          <span class="text-caption text-grey">Нет задач анализа</span>
        </div>

        <div
          v-for="task in sortedTasks"
          :key="task.id"
          class="d-flex flex-column"
          style="border-bottom: 1px solid rgba(0,0,0,0.05)"
        >
          <div
            class="analysis-panel__task"
            :class="{ 'analysis-panel__task--completed': task.status === 'COMPLETED' }"
          >
            <!-- Status icon + plugin name -->
            <div class="d-flex align-center gap-2 flex-1 min-w-0">
              <v-progress-circular
                v-if="task.status === 'PROCESSING' || task.status === 'PENDING'"
                size="14"
                width="2"
                indeterminate
                color="warning"
              />
              <v-icon v-else-if="task.status === 'COMPLETED'" size="14" color="success">mdi-check-circle</v-icon>
              <v-icon v-else-if="task.status === 'FAILED'" size="14" color="error">mdi-alert-circle</v-icon>
  
              <div class="min-w-0">
                <div class="text-caption font-weight-medium text-truncate" :title="pluginLabel(task.pluginName)">
                  {{ pluginLabel(task.pluginName) }}
                </div>
                <div class="text-caption text-grey" style="font-size:10px">
                  {{ task.id.slice(0, 8) }}
                </div>
              </div>
            </div>
  
            <!-- Actions -->
            <div class="d-flex align-center gap-1 flex-shrink-0">
              <!-- Layer visibility toggle for VECTOR / RASTER -->
              <template v-if="task.status === 'COMPLETED' && getStagingLayer(task.id)">
                <v-btn
                  :icon="isLayerVisible(task.id) ? 'mdi-eye' : 'mdi-eye-off'"
                  size="x-small"
                  variant="text"
                  :color="isLayerVisible(task.id) ? layerColor(task.id) : 'grey'"
                  :title="isLayerVisible(task.id) ? 'Скрыть слой' : 'Показать слой'"
                  @click="toggleLayerVisibility(task.id)"
                />
                <!-- Commit button -->
                <v-btn
                  icon="mdi-content-save-outline"
                  size="x-small"
                  variant="text"
                  color="success"
                  title="Сохранить в проект"
                  @click="openCommitDialog(task)"
                />
                <!-- Reject button -->
                <v-btn
                  icon="mdi-delete-sweep-outline"
                  size="x-small"
                  variant="text"
                  color="error"
                  title="Отклонить и удалить результаты"
                  @click="confirmReject(task)"
                />
              </template>
  
              <!-- JSON stats table button (zonal_statistics) -->
              <v-btn
                v-if="task.status === 'COMPLETED' && hasJsonOutput(task)"
                icon="mdi-table"
                size="x-small"
                variant="text"
                color="blue"
                title="Просмотр таблицы статистики"
                :loading="loadingStats === task.id"
                @click="openStatsDialog(task)"
              />
  
              <!-- Error tooltip -->
              <v-tooltip v-if="task.status === 'FAILED'" :text="task.errorMessage || 'Ошибка'" location="left">
                <template #activator="{ props }">
                  <v-icon v-bind="props" size="14" color="error">mdi-information-outline</v-icon>
                </template>
              </v-tooltip>
  
              <!-- Remove staging layer -->
              <v-btn
                v-if="getStagingLayer(task.id)"
                icon="mdi-close"
                size="x-small"
                variant="text"
                color="grey"
                title="Убрать слой с карты"
                @click="removeLayer(task.id)"
              />
            </div>
          </div>
          <!-- Style select for raster staging layers -->
          <div v-if="getStagingLayer(task.id)?.type === 'RASTER' && isLayerVisible(task.id)" class="px-7 pb-2 mt-n1 d-flex flex-column gap-1">
            <v-checkbox
              :model-value="getUseTiTilerColormap(task.id)"
              label="Встроенная шкала TiTiler"
              density="compact"
              hide-details
              class="mt-0 mb-1"
              style="font-size: 11px;"
              @update:model-value="toggleStagingTiTilerColormap(task.id, $event)"
            />
            <v-select
              v-if="getUseTiTilerColormap(task.id)"
              :model-value="getStagingColormapId(task.id)"
              :items="titilerColormaps"
              label="Шкала TiTiler"
              density="compact"
              variant="outlined"
              hide-details
              style="font-size: 11px; max-width: 190px;"
              class="style-select mb-1"
              @update:model-value="changeStagingColormapId(task.id, $event)"
            />
            <v-select
              v-else
              :model-value="getStyleValue(task.id)"
              :items="styleSelectItems"
              item-title="title"
              item-value="id"
              label="Стиль интерполяции"
              density="compact"
              variant="outlined"
              hide-details
              style="font-size: 11px; max-width: 190px;"
              class="style-select mb-1"
              clearable
              placeholder="Без стиля (Полутоновый)"
              @update:model-value="changeRasterStyle(task.id, $event)"
            />
            <v-select
              :model-value="getInterpolationValue(task.id)"
              :items="['nearest', 'bilinear', 'cubic', 'cubic_spline', 'lanczos', 'average', 'mode']"
              label="Метод resampling"
              density="compact"
              variant="outlined"
              hide-details
              style="font-size: 11px; max-width: 190px;"
              class="style-select"
              @update:model-value="changeInterpolation(task.id, $event)"
            />
          </div>
        </div>
      </div>
    </v-expand-transition>

    <!-- Commit Dialog -->
    <v-dialog v-model="commitDialog" max-width="500">
      <v-card rounded="lg" class="commit-dialog-card">
        <v-card-title class="d-flex align-center gap-2 pa-4">
          <v-icon color="success">mdi-content-save</v-icon>
          <span>Сохранить результаты анализа</span>
          <v-spacer />
          <v-btn icon="mdi-close" size="small" variant="text" @click="commitDialog = false" />
        </v-card-title>
        
        <v-divider />

        <v-card-text class="pa-4">
          <div class="mb-4">
            <span class="text-caption text-grey">Вы собираетесь перенести временные результаты анализа в постоянные слои проекта.</span>
          </div>

          <v-text-field
            v-model="commitTaskName"
            label="Префикс названия объектов"
            variant="outlined"
            density="compact"
            class="mb-3"
            hide-details
          />

          <!-- Folder selection only for Vector staging layers -->
          <v-select
            v-if="commitLayerType === 'VECTOR'"
            v-model="commitFolderId"
            :items="folderItems"
            item-title="name"
            item-value="id"
            label="Папка проекта (Куда сохранить)"
            variant="outlined"
            density="compact"
            clearable
            hide-details
          />
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" @click="commitDialog = false">Отмена</v-btn>
          <v-btn color="success" variant="flat" :loading="committing" @click="executeCommit">Сохранить</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Statistics dialog -->
    <v-dialog v-model="statsDialog" max-width="900" scrollable>
      <v-card class="stats-dialog-card" rounded="lg">
        <v-card-title class="d-flex align-center gap-2 pa-4">
          <v-icon color="blue">mdi-chart-bar</v-icon>
          <span>Статистика: {{ statsDialogTitle }}</span>
          <v-spacer />
          <v-btn icon="mdi-close" size="small" variant="text" @click="statsDialog = false" />
        </v-card-title>

        <v-divider />

        <v-card-text class="pa-0">
          <!-- Loading skeleton -->
          <div v-if="statsLoading" class="d-flex justify-center align-center pa-8">
            <v-progress-circular indeterminate color="blue" size="40" />
          </div>

          <!-- Error -->
          <div v-else-if="statsError" class="pa-6 text-center">
            <v-icon size="40" color="error" class="mb-2">mdi-alert-circle-outline</v-icon>
            <div class="text-body-2 text-error">{{ statsError }}</div>
          </div>

          <!-- Table -->
          <div v-else-if="statsData.length > 0">
            <!-- Search + export toolbar -->
            <div class="d-flex align-center gap-2 pa-3">
              <v-text-field
                v-model="statsSearch"
                prepend-inner-icon="mdi-magnify"
                placeholder="Поиск..."
                density="compact"
                variant="outlined"
                hide-details
                clearable
                style="max-width: 260px"
              />
              <v-spacer />
              <v-chip size="small" color="blue-grey" variant="tonal">
                {{ statsData.length }} зон
              </v-chip>
              <v-btn
                prepend-icon="mdi-download"
                size="small"
                variant="tonal"
                color="blue"
                @click="exportCsv"
              >
                CSV
              </v-btn>
            </div>

            <v-data-table
              :headers="statsHeaders"
              :items="statsData"
              :search="statsSearch"
              density="compact"
              class="stats-table"
              hover
              fixed-header
              height="420"
              :items-per-page="50"
            >
              <template #item="{ item }">
                <tr>
                  <td
                    v-for="col in statsHeaders"
                    :key="col.key"
                    class="text-caption"
                    :class="{ 'text-right font-weight-medium': isNumericKey(col.key) }"
                  >
                    {{ formatCell(item[col.key], col.key) }}
                  </td>
                </tr>
              </template>
            </v-data-table>
          </div>

          <div v-else class="pa-6 text-center text-grey">
            Нет данных
          </div>
        </v-card-text>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useStore } from 'vuex';
import axios from 'axios';
import type { AnalysisTask, RasterStyle } from '@/types/api';
import geoAbstractionService from '@/services/geo-abstraction.service';
import RasterStyleService from '@/services/raster-style.service';
import { buildTiTilerColormap } from '@/util/titiler-style-builder';

const props = defineProps<{
  /** Ref to map staging layer visibility toggle from useStagingLayers */
  setVisible?: (taskId: string, visible: boolean) => void;
}>();

const store = useStore();

// ── State ─────────────────────────────────────────────────────────────────────
const expanded = ref(true);
const loadingStats = ref<string | null>(null);
const layerVisibility = ref<Record<string, boolean>>({});

// Dialog
const statsDialog = ref(false);
const statsLoading = ref(false);
const statsError = ref<string | null>(null);
const statsData = ref<Record<string, unknown>[]>([]);
const statsHeaders = ref<{ title: string; key: string; sortable: boolean }[]>([]);
const statsDialogTitle = ref('');
const statsSearch = ref('');

// Commit Dialog State
const commitDialog = ref(false);
const committing = ref(false);
const commitTaskName = ref('');
const commitFolderId = ref<string | null>(null);
const commitLayerType = ref<'VECTOR' | 'RASTER'>('VECTOR');
const commitTaskId = ref('');

const folderItems = computed(() => {
  return (store.state.geodata.folders || []).map((f: any) => ({
    name: f.name,
    id: f.id
  }));
});

// ── Getters ───────────────────────────────────────────────────────────────────
const analysisTasks = computed<AnalysisTask[]>(() => store.state.geodata.analysisTasks || []);
const stagingLayers = computed<{ taskId: string; type: string; url: string; s3Url?: string; interpolation?: string; colormap?: string; styleId?: string | null; colormapId?: string | null; style?: any; label: string; pluginName?: string }[]>(
  () => store.state.geodata.stagingLayers || []
);

const sortedTasks = computed(() =>
  [...analysisTasks.value].sort((a, b) => {
    const order: Record<string, number> = { PROCESSING: 0, PENDING: 1, COMPLETED: 2, FAILED: 3 };
    return (order[a.status] ?? 4) - (order[b.status] ?? 4);
  })
);

const pendingCount = computed(() =>
  analysisTasks.value.filter(t => t.status === 'PENDING' || t.status === 'PROCESSING').length
);
const completedCount = computed(() =>
  analysisTasks.value.filter(t => t.status === 'COMPLETED').length
);

function getStagingLayer(taskId: string) {
  return stagingLayers.value.find(l => l.taskId === taskId) ?? null;
}

function isLayerVisible(taskId: string): boolean {
  return layerVisibility.value[taskId] !== false; // default visible
}

function layerColor(taskId: string): string {
  const sl = getStagingLayer(taskId);
  return sl?.type === 'RASTER' ? 'teal' : 'deep-orange';
}

function hasJsonOutput(task: AnalysisTask): boolean {
  return !!(task.s3OutputPaths?.statistics_json);
}

const availableStyles = ref<RasterStyle[]>([]);
const titilerColormaps = ref<string[]>([]);

const styleSelectItems = computed(() => {
  return availableStyles.value.map(s => ({
    title: s.title,
    id: s.id
  }));
});

const fetchAvailableStyles = async () => {
  try {
    const response = await RasterStyleService.getRasterStyles(0, 100);
    availableStyles.value = response.data.content;
  } catch (err) {
    console.error('Failed to fetch raster styles for analysis panel:', err);
  }
};

const fetchTiTilerColormaps = async () => {
  try {
    titilerColormaps.value = await RasterStyleService.getTiTilerColorMaps();
  } catch (err) {
    console.error('Failed to fetch TiTiler colormaps for analysis panel:', err);
    titilerColormaps.value = ['cividis', 'inferno', 'magma', 'plasma', 'rdylgn', 'spectral', 'terrain', 'viridis'];
  }
};

onMounted(() => {
  fetchAvailableStyles();
  fetchTiTilerColormaps();
});

// Auto-apply specialized style for new raster staging layers
watch(
  [stagingLayers, availableStyles],
  ([layers, styles]) => {
    if (!layers || !styles || styles.length === 0) return;
    
    layers.forEach(l => {
      // If it is a RASTER layer and styleId is not set yet (undefined)
      if (l.type === 'RASTER' && l.styleId === undefined) {
        const task = analysisTasks.value.find(t => t.id === l.taskId);
        let targetStyleName = '';
        
        if (l.pluginName === 'aspect') {
          targetStyleName = 'aspect_orientation';
        } else if (l.pluginName === 'slope') {
          targetStyleName = 'slope_steepness';
        } else if (l.pluginName === 'viewshed_analysis') {
          targetStyleName = 'viewshed_visibility';
        } else if (l.pluginName === 'watershed_delineation') {
          targetStyleName = 'watershed_streams';
        } else if (l.pluginName === 'unsupervised_class') {
          targetStyleName = 'unsupervised_kmeans';
        } else if (l.pluginName === 'spectral_indices') {
          const idx = task?.inputParams?.index_type;
          if (idx) {
            switch (idx.toUpperCase()) {
              case 'NDVI':
              case 'SAVI':
              case 'EVI':
                targetStyleName = 'vegetation_index';
                break;
              case 'NDWI':
                targetStyleName = 'ndwi_water';
                break;
              case 'NBR':
                targetStyleName = 'nbr_burn';
                break;
              case 'NDRE':
                targetStyleName = 'ndre_index';
                break;
              case 'GNDVI':
                targetStyleName = 'gndvi';
                break;
              case 'NDMI':
                targetStyleName = 'ndmi_moisture';
                break;
              case 'NDSI':
                targetStyleName = 'ndsi_snow';
                break;
              case 'NDBI':
                targetStyleName = 'ndbi_urban';
                break;
            }
          }
        }
        
        if (targetStyleName) {
          const matchedStyle = styles.find(s => s.name === targetStyleName);
          if (matchedStyle) {
            changeRasterStyle(l.taskId, matchedStyle.id);
            return;
          }
        }
        
        // Default fallback: clear style to null so we don't check again
        store.commit('geodata/UPDATE_STAGING_LAYER_COLORMAP', { taskId: l.taskId, colormap: '', styleId: null });
      }
    });
  },
  { immediate: true, deep: true }
);

function getInterpolationValue(taskId: string): string {
  return getStagingLayer(taskId)?.interpolation || 'bilinear';
}

function changeInterpolation(taskId: string, val: string) {
  store.commit('geodata/UPDATE_STAGING_LAYER_INTERPOLATION', { taskId, interpolation: val });
}

function getStyleValue(taskId: string): string | null {
  return getStagingLayer(taskId)?.styleId || null;
}

function getUseTiTilerColormap(taskId: string): boolean {
  return !!getStagingLayer(taskId)?.colormapId;
}

function getStagingColormapId(taskId: string): string | null {
  return getStagingLayer(taskId)?.colormapId || null;
}

function toggleStagingTiTilerColormap(taskId: string, checked: boolean | null) {
  if (checked) {
    store.commit('geodata/UPDATE_STAGING_LAYER_COLORMAP', { taskId, colormap: '', styleId: null, colormapId: 'viridis' });
  } else {
    store.commit('geodata/UPDATE_STAGING_LAYER_COLORMAP', { taskId, colormap: '', styleId: null, colormapId: null });
  }
}

function changeStagingColormapId(taskId: string, colormapId: string | null) {
  store.commit('geodata/UPDATE_STAGING_LAYER_COLORMAP', { taskId, colormap: '', styleId: null, colormapId });
}

function changeRasterStyle(taskId: string, styleId: string | null) {
  let colormapStr = '';
  if (styleId) {
    const style = availableStyles.value.find(s => s.id === styleId);
    if (style && style.config) {
      colormapStr = buildTiTilerColormap(style.config);
    }
  }
  store.commit('geodata/UPDATE_STAGING_LAYER_COLORMAP', { taskId, colormap: colormapStr, styleId, colormapId: null });
}

// ── Labels ────────────────────────────────────────────────────────────────────
const PLUGIN_LABELS: Record<string, string> = {
  terrain_contours:    'Изолинии рельефа',
  zonal_statistics:    'Зональная статистика',
  clip_raster_by_mask: 'Обрезка растра',
  slope:               'Крутизна склонов',
  aspect:              'Экспозиция склонов',
  hillshade:           'Теневая отмывка',
  viewshed_analysis:   'Анализ видимости',
  spectral_indices:    'Спектральные индексы',
  raster_reclass:      'Реклассификация растра',
  raster_algebra:      'Алгебра карт',
  polygonize_raster:   'Векторизация растра',
  rasterize_vector:    'Растеризация векторов',
  raster_mosaic:       'Сшивка мозаики',
  unsupervised_class:  'Неконтролируемая классификация',
  watershed_delineation:'Выделение водосборов'
};

function pluginLabel(name: string): string {
  return PLUGIN_LABELS[name] ?? name;
}

// ── Actions ───────────────────────────────────────────────────────────────────
function toggleLayerVisibility(taskId: string) {
  const current = isLayerVisible(taskId);
  layerVisibility.value = { ...layerVisibility.value, [taskId]: !current };
  props.setVisible?.(taskId, !current);
}

function removeLayer(taskId: string) {
  // Reset visibility entry
  const next = { ...layerVisibility.value };
  delete next[taskId];
  layerVisibility.value = next;
  store.dispatch('geodata/removeStagingLayer', taskId);
}

function openCommitDialog(task: AnalysisTask) {
  commitTaskId.value = task.id;
  commitTaskName.value = pluginLabel(task.pluginName);
  commitFolderId.value = null;
  const sl = getStagingLayer(task.id);
  commitLayerType.value = sl?.type as 'VECTOR' | 'RASTER' || 'VECTOR';
  commitDialog.value = true;
}

async function executeCommit() {
  if (!commitTaskId.value) return;
  committing.value = true;
  try {
    await store.dispatch('geodata/commitStagingLayer', {
      taskId: commitTaskId.value,
      projectId: store.state.geodata.selectedProjectId,
      folderId: commitFolderId.value,
      taskName: commitTaskName.value
    });
    commitDialog.value = false;
  } catch (err) {
    console.error(err);
  } finally {
    committing.value = false;
  }
}

async function confirmReject(task: AnalysisTask) {
  if (confirm(`Вы уверены, что хотите отклонить результаты анализа «${pluginLabel(task.pluginName)}»? Это действие безвозвратно удалит временные геометрии и файлы из базы данных и облачного хранилища.`)) {
    try {
      await store.dispatch('geodata/rejectStagingLayer', {
        taskId: task.id,
        projectId: store.state.geodata.selectedProjectId
      });
    } catch (err) {
      console.error(err);
    }
  }
}

// ── Statistics dialog ─────────────────────────────────────────────────────────
async function openStatsDialog(task: AnalysisTask) {
  statsDialog.value = true;
  statsLoading.value = true;
  statsError.value = null;
  statsData.value = [];
  statsHeaders.value = [];
  statsSearch.value = '';
  statsDialogTitle.value = `${pluginLabel(task.pluginName)} (${task.id.slice(0, 8)})`;

  try {
    const urlRes = await geoAbstractionService.getAnalysisTaskOutputUrl(task.id, 'statistics_json');
    const jsonRes = await axios.get<Record<string, unknown>[]>(urlRes.data.url);
    const rows = jsonRes.data;

    if (!Array.isArray(rows) || rows.length === 0) {
      statsError.value = 'Данные статистики пусты или имеют неверный формат';
      return;
    }

    // Auto-detect columns from the first row's keys
    const keys = Object.keys(rows[0]);
    statsHeaders.value = keys.map(k => ({
      title: formatHeaderLabel(k),
      key: k,
      sortable: true,
    }));
    statsData.value = rows;
  } catch (err: unknown) {
    statsError.value = err instanceof Error ? err.message : 'Не удалось загрузить данные';
  } finally {
    statsLoading.value = false;
  }
}

// ── Formatting helpers ────────────────────────────────────────────────────────
function formatHeaderLabel(key: string): string {
  return key
    .replace(/_/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase());
}

function isNumericKey(key: string): boolean {
  const sample = statsData.value[0]?.[key];
  return typeof sample === 'number';
}

function formatCell(value: unknown, key: string): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'number') {
    return isNumericKey(key)
      ? value % 1 === 0
        ? value.toLocaleString('ru-RU')
        : value.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
      : String(value);
  }
  return String(value);
}

// ── CSV Export ────────────────────────────────────────────────────────────────
function exportCsv() {
  if (!statsData.value.length || !statsHeaders.value.length) return;
  const keys = statsHeaders.value.map(h => h.key);
  const header = statsHeaders.value.map(h => h.title).join(',');
  const rows = statsData.value
    .map(row => keys.map(k => JSON.stringify(row[k] ?? '')).join(','))
    .join('\n');
  const blob = new Blob([header + '\n' + rows], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `stats_${statsDialogTitle.value.replace(/[^a-z0-9]/gi, '_')}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
.analysis-panel {
  font-size: 12px;
}

.analysis-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  cursor: pointer;
  user-select: none;
  border-radius: 6px;
  transition: background 0.15s;
}
.analysis-panel__header:hover {
  background: rgba(255, 107, 53, 0.08);
}

.analysis-panel__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  color: #9e9e9e;
}

.analysis-panel__task {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  margin: 2px 0;
  border-radius: 6px;
  border-left: 2px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  min-width: 0;
}
.analysis-panel__task:hover {
  background: rgba(0, 0, 0, 0.04);
}
.analysis-panel__task--completed {
  border-left-color: #4caf50;
}

.stats-dialog-card {
  background: #1e1e2e !important;
  color: #cdd6f4 !important;
}

.stats-table :deep(th) {
  background: #181825 !important;
  color: #89b4fa !important;
  font-weight: 600 !important;
  font-size: 11px !important;
  white-space: nowrap;
}
.stats-table :deep(td) {
  font-size: 11px !important;
  padding: 4px 12px !important;
  border-bottom: 1px solid rgba(255,255,255,0.05) !important;
}
.stats-table :deep(tr:hover td) {
  background: rgba(137, 180, 250, 0.06) !important;
}

.gap-2 { gap: 8px; }
.gap-1 { gap: 4px; }
.min-w-0 { min-width: 0; }
.flex-shrink-0 { flex-shrink: 0; }
.flex-1 { flex: 1; }
</style>
