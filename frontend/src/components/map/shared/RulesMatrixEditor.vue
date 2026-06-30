<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{
  modelValue: number[][] | null;
}>();

const emit = defineEmits(['update:modelValue']);

interface RuleItem {
  min: number | null;
  max: number | null;
  value: number | null;
}

// Преобразуем входящий number[][] во внутренний массив объектов
const rules = ref<RuleItem[]>([]);

const initRules = () => {
  if (props.modelValue && props.modelValue.length > 0) {
    rules.value = props.modelValue.map(r => ({
      min: r[0] !== undefined ? r[0] : null,
      max: r[1] !== undefined ? r[1] : null,
      value: r[2] !== undefined ? r[2] : null
    }));
  } else {
    rules.value = [{ min: 0, max: 100, value: 1 }];
  }
};

initRules();

// Следим за изменениями внешнего значения
watch(() => props.modelValue, () => {
  // Чтобы избежать бесконечного цикла, обновляем только если длина или значения изменились
  const currentSerialized = JSON.stringify(rules.value.map(r => [r.min, r.max, r.value]));
  const propSerialized = JSON.stringify(props.modelValue || []);
  if (currentSerialized !== propSerialized) {
    initRules();
  }
}, { deep: true });

// Следим за изменениями внутренних правил и отправляем наружу
watch(rules, (newRules) => {
  const serialized = newRules.map(r => [
    r.min === null ? 0 : Number(r.min),
    r.max === null ? 0 : Number(r.max),
    r.value === null ? 0 : Number(r.value)
  ]);
  emit('update:modelValue', serialized);
}, { deep: true });

function addRule() {
  const lastRule = rules.value[rules.value.length - 1];
  const nextMin = lastRule && lastRule.max !== null ? lastRule.max : 0;
  const nextMax = lastRule && lastRule.max !== null ? lastRule.max + 100 : 100;
  rules.value.push({ min: nextMin, max: nextMax, value: 1 });
}

function removeRule(index: number) {
  rules.value.splice(index, 1);
  if (rules.value.length === 0) {
    rules.value.push({ min: null, max: null, value: null });
  }
}
</script>

<template>
  <div class="rules-matrix-editor">
    <v-row v-for="(rule, index) in rules" :key="index" class="align-center mb-2" dense>
      <v-col cols="3">
        <v-text-field
          v-model.number="rule.min"
          label="Min (вкл.)"
          type="number"
          variant="outlined"
          density="compact"
          hide-details
          required
        ></v-text-field>
      </v-col>
      <v-col cols="3">
        <v-text-field
          v-model.number="rule.max"
          label="Max (искл.)"
          type="number"
          variant="outlined"
          density="compact"
          hide-details
          required
        ></v-text-field>
      </v-col>
      <v-col cols="4">
        <v-text-field
          v-model.number="rule.value"
          label="Значение"
          type="number"
          variant="outlined"
          density="compact"
          hide-details
          required
        ></v-text-field>
      </v-col>
      <v-col cols="2" class="text-right">
        <v-btn
          icon="mdi-delete"
          variant="text"
          color="error"
          size="small"
          :disabled="rules.length <= 1"
          @click="removeRule(index)"
        ></v-btn>
      </v-col>
    </v-row>

    <v-btn
      prepend-icon="mdi-plus"
      variant="outlined"
      color="primary"
      size="small"
      class="mt-1 mb-3"
      @click="addRule"
    >
      Добавить интервал
    </v-btn>
  </div>
</template>
