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
      </div>
    </v-expand-transition>

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
import { ref, computed } from 'vue';
import { useStore } from 'vuex';
import axios from 'axios';
import type { AnalysisTask } from '@/types/api';
import geoAbstractionService from '@/services/geo-abstraction.service';

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

// ── Getters ───────────────────────────────────────────────────────────────────
const analysisTasks = computed<AnalysisTask[]>(() => store.state.geodata.analysisTasks || []);
const stagingLayers = computed<{ taskId: string; type: string; url: string; label: string }[]>(
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

// ── Labels ────────────────────────────────────────────────────────────────────
const PLUGIN_LABELS: Record<string, string> = {
  terrain_contours:   'Изолинии рельефа',
  zonal_statistics:   'Зональная статистика',
  clip_raster_by_mask:'Обрезка растра',
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
