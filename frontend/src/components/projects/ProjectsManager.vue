<template>
  <v-card>
    <v-toolbar color="primary" dark>
      <v-toolbar-title>Projects</v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn icon @click="openCreateDialog">
        <v-icon>mdi-plus</v-icon>
      </v-btn>
    </v-toolbar>

    <v-progress-linear :active="isLoading" indeterminate color="primary"></v-progress-linear>

    <v-list lines="two">
      <v-list-item
        v-for="project in projects"
        :key="project.id"
        :title="project.name"
        :subtitle="project.description"
      >
        <template v-slot:append>
          <v-btn icon="mdi-map" color="primary" variant="text" @click="navigateToMap(project.id)" title="Open Map"></v-btn>
          <v-btn icon="mdi-pencil" variant="text" @click="openEditDialog(project)"></v-btn>
          <v-btn icon="mdi-share-variant" variant="text" color="primary" @click="openShareDialog(project)" title="Share Project"></v-btn>
          <v-btn icon="mdi-delete" variant="text" color="error" @click="deleteProject(project.id)"></v-btn>
        </template>
      </v-list-item>
    </v-list>

    <div class="text-center">
        <v-pagination
            v-model="currentPage"
            :length="totalPages"
            rounded="circle"
        ></v-pagination>
    </div>

    <v-dialog v-model="dialog" max-width="600px">
      <v-card>
        <v-card-title>
          <span class="headline">{{ isEditing ? 'Edit Project' : 'New Project' }}</span>
        </v-card-title>
        <v-card-text>
          <v-form ref="form">
            <v-text-field
              v-model="editableProject.name"
              label="Project Name"
              :rules="[v => !!v || 'Name is required']"
              required
            ></v-text-field>
            <v-textarea
              v-model="editableProject.description"
              label="Description"
            ></v-textarea>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="blue darken-1" text @click="dialog = false">Cancel</v-btn>
          <v-btn color="blue darken-1" text @click="saveProject">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="shareDialog" max-width="500px">
      <v-card>
        <v-card-title>
          <span class="headline">Share Project: {{ projectToShare?.name }}</span>
        </v-card-title>
        <v-card-text>
          <v-form ref="shareForm">
            <v-text-field
              v-model="shareEmail"
              label="User Email"
              :rules="[v => !!v || 'Email is required', v => /.+@.+\..+/.test(v) || 'E-mail must be valid']"
              required
            ></v-text-field>
            <v-select
              v-model="sharePermissionLevel"
              :items="['READ_ONLY']"
              label="Permission Level"
              required
            ></v-select>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="blue darken-1" text @click="shareDialog = false">Cancel</v-btn>
          <v-btn color="blue darken-1" text @click="executeShare">Share</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import type { Project } from '@/types/api';

// Используем Vuex store и Vue Router
const store = useStore();
const router = useRouter();

// --- Состояние компонента ---
const dialog = ref(false);
const isEditing = ref(false);
const editableProject = ref<Partial<Project>>({});
const form = ref<any>(null); // Для доступа к методам v-form
const currentPage = ref(1);
const pageSize = ref(10);

// --- Состояние для Share Dialog ---
const shareDialog = ref(false);
const projectToShare = ref<Project | null>(null);
const shareEmail = ref('');
const sharePermissionLevel = ref('READ_ONLY');
const shareForm = ref<any>(null);

// --- Получение данных из Vuex ---
const isLoading = computed(() => store.state.geodata.isLoading);
const projects = computed<Project[]>(() => store.state.geodata.projects?.content || []);
const totalPages = computed(() => store.state.geodata.projects?.totalPages || 0);

// --- Методы для загрузки данных ---
const fetchCurrentPage = () => {
    // В v-pagination страницы начинаются с 1, в API - с 0
    store.dispatch('geodata/fetchProjects', { page: currentPage.value - 1, size: pageSize.value });
}

// --- Жизненный цикл и наблюдатели ---
onMounted(() => {
  fetchCurrentPage();
});

watch(currentPage, () => {
    fetchCurrentPage();
});

// --- Методы ---
const navigateToMap = (projectId: string) => {
    router.push(`/projects/${projectId}`);
};

const openCreateDialog = () => {
  isEditing.value = false;
  editableProject.value = { name: '', description: '' };
  dialog.value = true;
};

const openEditDialog = (project: Project) => {
  isEditing.value = true;
  // Клонируем объект, чтобы избежать прямого изменения состояния
  editableProject.value = { ...project };
  dialog.value = true;
};

const saveProject = async () => {
  const { valid } = await form.value.validate();
  if (!valid) return;

  const actionPayload = {
      projectData: editableProject.value,
      page: currentPage.value - 1,
      size: pageSize.value
  };

  if (isEditing.value) {
    await store.dispatch('geodata/updateProject', actionPayload);
  } else {
    await store.dispatch('geodata/createProject', actionPayload);
  }
  
  dialog.value = false;
};

const openShareDialog = (project: Project) => {
  projectToShare.value = project;
  shareEmail.value = '';
  sharePermissionLevel.value = 'READ_ONLY';
  shareDialog.value = true;
};

const executeShare = async () => {
  const { valid } = await shareForm.value.validate();
  if (!valid || !projectToShare.value) return;

  try {
    await store.dispatch('geodata/shareProject', {
      projectId: projectToShare.value.id,
      email: shareEmail.value,
      permissionLevel: sharePermissionLevel.value,
    });
    // Тут можно показать сообщение об успехе
    shareDialog.value = false;
  } catch (error) {
    // Тут можно показать сообщение об ошибке
    console.error("Failed to share project:", error);
  }
};

const deleteProject = async (id: string) => {
  if (confirm('Are you sure you want to delete this project?')) {
      const actionPayload = { 
          projectId: id, 
          page: currentPage.value - 1, 
          size: pageSize.value 
      };
      await store.dispatch('geodata/deleteProject', actionPayload);
  }
};
</script>

<style scoped>
.headline {
  font-weight: 500;
}
</style>
