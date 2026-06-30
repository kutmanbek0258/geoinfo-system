<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';

const props = defineProps<{
  modelValue: { x: number; y: number } | null;
  title: string;
}>();

const emit = defineEmits(['update:modelValue', 'start-selection', 'end-selection']);

const store = useStore();

const xCoord = computed(() => props.modelValue?.x ?? null);
const yCoord = computed(() => props.modelValue?.y ?? null);

const isWaitingForPoint = ref(false);

const isSelectingOnMap = computed(() => store.state.geodata.pointSelectionActive && isWaitingForPoint.value);

watch(() => store.state.geodata.selectedPoint, (newPoint) => {
  if (newPoint && isWaitingForPoint.value) {
    emit('update:modelValue', {
      x: Number(newPoint.x.toFixed(6)),
      y: Number(newPoint.y.toFixed(6))
    });
    isWaitingForPoint.value = false;
    store.commit('geodata/SET_SELECTED_POINT', null);
    store.commit('geodata/SET_POINT_SELECTION_ACTIVE', false);
    emit('end-selection');
  }
});

function startMapSelection() {
  isWaitingForPoint.value = true;
  store.commit('geodata/SET_POINT_SELECTION_ACTIVE', true);
  emit('start-selection');
}
</script>

<template>
  <div class="map-point-picker border rounded pa-3 mb-3 bg-grey-lighten-4">
    <div class="text-subtitle-2 mb-2 d-flex align-center">
      <v-icon icon="mdi-map-marker" start color="primary"></v-icon>
      {{ title }}
    </div>

    <v-row dense class="align-center">
      <v-col cols="5">
        <v-text-field
          :model-value="xCoord"
          label="Долгота (X)"
          type="number"
          density="compact"
          variant="outlined"
          hide-details
          readonly
          placeholder="Не указано"
        ></v-text-field>
      </v-col>
      <v-col cols="5">
        <v-text-field
          :model-value="yCoord"
          label="Широта (Y)"
          type="number"
          density="compact"
          variant="outlined"
          hide-details
          readonly
          placeholder="Не указано"
        ></v-text-field>
      </v-col>
      <v-col cols="2" class="text-right">
        <v-btn
          :color="isSelectingOnMap ? 'warning' : 'primary'"
          icon="mdi-crosshairs-gps"
          size="small"
          :loading="isSelectingOnMap"
          @click="startMapSelection"
          v-tooltip:top="isSelectingOnMap ? 'Кликните на карте' : 'Указать на карте'"
        ></v-btn>
      </v-col>
    </v-row>
  </div>
</template>

<style scoped>
.map-point-picker {
  border: 1px solid #e0e0e0 !important;
}
</style>
