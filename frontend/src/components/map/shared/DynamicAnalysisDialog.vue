<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import DynamicSchemaForm from './DynamicSchemaForm.vue';
import type { CreateAnalysisTaskDto } from '@/types/api';

const props = defineProps<{
  show: boolean;
  pluginName: string;
}>();

const emit = defineEmits(['update:show', 'task-created']);

const store = useStore();
const loading = ref(false);
const valid = ref(true);

const formValue = ref({
  inputs: {} as Record<string, any>,
  parameters: {} as Record<string, any>
});

const internalShow = ref(props.show);

watch(() => props.show, (newVal) => {
  internalShow.value = newVal;
});

watch(internalShow, (newVal) => {
  emit('update:show', newVal);
});

// Находим схему по имени плагина
const pluginSchema = computed(() => {
  return store.state.geodata.pluginSchemas.find((s: any) => s.pluginName === props.pluginName);
});

const schema = computed(() => pluginSchema.value?.schema || null);
const title = computed(() => pluginSchema.value?.title || 'Геоанализ');
const icon = computed(() => pluginSchema.value?.icon || 'mdi-puzzle-outline');

function handleStartSelection() {
  internalShow.value = false;
}

function handleEndSelection() {
  internalShow.value = true;
}

async function runAnalysis() {
  if (!schema.value) return;
  loading.value = true;
  try {
    const dto: CreateAnalysisTaskDto = {
      pluginName: props.pluginName,
      projectId: store.state.geodata.selectedProjectId ?? undefined,
      inputs: formValue.value.inputs,
      parameters: formValue.value.parameters
    };

    const task = await store.dispatch('geodata/triggerAnalysis', dto);
    emit('task-created', task);
    internalShow.value = false;
  } catch (err) {
    console.error('Failed to trigger dynamic analysis:', err);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div>
    <v-dialog eager v-model="internalShow" max-width="550px">
      <v-card v-if="schema">
        <v-card-title class="pa-4 bg-primary text-white d-flex align-center">
          <v-icon start :icon="icon" class="mr-2"></v-icon>
          {{ title }}
        </v-card-title>

        <v-card-text class="pa-4">
          <DynamicSchemaForm
            :schema="schema"
            v-model="formValue"
            @start-selection="handleStartSelection"
            @end-selection="handleEndSelection"
          />
        </v-card-text>

        <v-divider></v-divider>

        <v-card-actions class="pa-4">
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="internalShow = false">Отмена</v-btn>
          <v-btn color="primary" @click="runAnalysis" :loading="loading" :disabled="!valid">
            Запустить анализ
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
