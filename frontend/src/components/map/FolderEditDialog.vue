<template>
  <v-dialog v-model="internalValue" max-width="500px">
    <v-card>
      <v-card-title>
        <span class="text-h5">{{ isEdit ? 'Редактировать папку' : 'Создать папку' }}</span>
      </v-card-title>
      <v-card-text>
        <v-form ref="form" v-model="valid">
          <v-text-field
            v-model="name"
            label="Название"
            :rules="[v => !!v || 'Название обязательно']"
            required
            density="comfortable"
          ></v-text-field>
          <v-textarea
            v-model="description"
            label="Описание"
            density="comfortable"
            rows="3"
          ></v-textarea>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="grey-darken-1" variant="text" @click="internalValue = false">Отмена</v-btn>
        <v-btn
          color="primary"
          variant="elevated"
          @click="save"
          :disabled="!valid || loading"
          :loading="loading"
        >
          Сохранить
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import type { GeoFolder } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  projectId: string;
  parentId?: string | null;
  layerId?: string | null;
  folder?: GeoFolder | null;
}>();

const emit = defineEmits(['update:modelValue', 'saved']);

const store = useStore();
const valid = ref(false);
const loading = ref(false);

const name = ref('');
const description = ref('');

const isEdit = computed(() => !!props.folder);

const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    if (props.folder) {
      name.value = props.folder.name;
      description.value = props.folder.description || '';
    } else {
      name.value = '';
      description.value = '';
    }
  }
});

const save = async () => {
  loading.value = true;
  try {
    if (isEdit.value && props.folder) {
      await store.dispatch('geodata/updateFolder', {
        ...props.folder,
        name: name.value,
        description: description.value
      });
    } else {
      await store.dispatch('geodata/createFolder', {
        projectId: props.projectId,
        parentId: props.parentId || null,
        layerId: props.layerId || null,
        name: name.value,
        description: description.value,
        characteristics: { visible: true }
      });
    }
    emit('saved');
    internalValue.value = false;
  } catch (error) {
    console.error('Failed to save folder', error);
  } finally {
    loading.value = false;
  }
};
</script>
