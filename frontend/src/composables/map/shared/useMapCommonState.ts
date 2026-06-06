import { computed, ref, watch } from 'vue';
import { useStore } from 'vuex';
import type { ImageryLayer, TerrainLayer, ProjectPoint, ProjectMultiline, ProjectPolygon } from '@/types/api';

export function useMapCommonState(projectId: string) {
  const store = useStore();

  // --- Vuex Data ---
  const imageryLayers = computed<ImageryLayer[]>(() => store.state.geodata.imageryLayers?.content || []);
  const terrainLayers = computed<TerrainLayer[]>(() => store.state.geodata.terrainLayers?.content || []);
  const points = computed<ProjectPoint[]>(() => store.state.geodata.points);
  const multilines = computed<ProjectMultiline[]>(() => store.state.geodata.multilines);
  const polygons = computed<ProjectPolygon[]>(() => store.state.geodata.polygons);

  // --- Selection State ---
  const selectedFeatureId = computed(() => store.state.geodata.selectedFeatureId);
  
  const selectedFeature = computed(() => {
    if (!selectedFeatureId.value) return null;

    const point = points.value.find(f => f.id === selectedFeatureId.value);
    if (point) return { ...point, type: 'Point' as const };

    const multiline = multilines.value.find(f => f.id === selectedFeatureId.value);
    if (multiline) return { ...multiline, type: 'MultiLineString' as const };

    const polygon = polygons.value.find(f => f.id === selectedFeatureId.value);
    if (polygon) return { ...polygon, type: 'Polygon' as const };

    return null;
  });

  // --- Visibility Filter ---
  const hiddenFeatureIds = computed(() => {
    const hidden = new Set<string>();
    points.value.forEach(p => { if (p.characteristics?.visible === false) hidden.add(p.id); });
    multilines.value.forEach(l => { if (l.characteristics?.visible === false) hidden.add(l.id); });
    polygons.value.forEach(p => { if (p.characteristics?.visible === false) hidden.add(p.id); });
    return hidden;
  });

  // --- Common UI Toggles ---
  const autoExtentEnabled = ref(false);
  const isGeometryEditMode = ref(false);
  const drawMode = ref<'Point' | 'MultiLineString' | 'Polygon' | null>(null);
  const measureMode = ref<'length' | 'area' | null>(null);
  const isBufferMode = ref(false);

  // --- Actions ---
  const selectFeature = (payload: string | { id: string | null, source?: 'map' | 'list' } | null) => {
    store.dispatch('geodata/selectFeature', payload);
  };

  return {
    // Data
    imageryLayers,
    terrainLayers,
    points,
    multilines,
    polygons,
    hiddenFeatureIds,
    
    // Selection
    selectedFeatureId,
    selectedFeature,
    selectFeature,

    // UI State
    autoExtentEnabled,
    isGeometryEditMode,
    drawMode,
    measureMode,
    isBufferMode,
  };
}
