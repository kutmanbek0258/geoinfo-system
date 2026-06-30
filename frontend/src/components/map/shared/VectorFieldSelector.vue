<script setup lang="ts">
import { computed } from 'vue';
import { useStore } from 'vuex';

const props = defineProps<{
  modelValue: string;
  title: string;
  vectorSource: any;
}>();

const emit = defineEmits(['update:modelValue']);

const store = useStore();

const availableFields = computed(() => {
  if (!props.vectorSource) return [];

  const sourceId = props.vectorSource.id;
  const sourceType = props.vectorSource.type;

  let features = [];
  if (sourceType === 'VECTOR_LAYER') {
    // Выбираем полигоны, соответствующие выбранной папке
    features = store.state.geodata.polygons.filter((p: any) => p.folderId === sourceId);
  }

  // Собираем все уникальные ключи свойств
  const fieldsSet = new Set<string>();
  features.forEach((f: any) => {
    if (f.properties) {
      Object.keys(f.properties).forEach(key => fieldsSet.add(key));
    }
  });

  return Array.from(fieldsSet);
});

const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});
</script>

<template>
  <div class="vector-field-selector mb-3">
    <v-combobox
      v-model="internalValue"
      :items="availableFields"
      :label="title"
      placeholder="Введите или выберите поле атрибута"
      variant="outlined"
      density="comfortable"
      hide-details
      clearable
      hint="Поле из таблицы атрибутов векторного слоя для извлечения значений"
      persistent-hint
    ></v-combobox>
  </div>
</template>
