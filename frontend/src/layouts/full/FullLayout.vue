<script setup lang="ts">
import { computed } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import MainView from './Main.vue';

const route = useRoute();
const isEditorPage = computed(() => route.name === 'OnlyOfficeEditor');
</script>

<template>
    <v-locale-provider >
        <v-app>
            <MainView v-if="!isEditorPage" />
            <v-main>
                <v-container fluid :class="isEditorPage ? 'editor-wrapper' : 'page-wrapper'">
                    <div :class="isEditorPage ? 'editor-max-width' : 'maxWidth'">
                        <RouterView />
                    </div>
                </v-container>
            </v-main>
        </v-app>
    </v-locale-provider>
</template>

<style scoped>
.editor-wrapper {
    padding: 0;
    height: 100%;
    max-width: 100% !important;
}
.editor-max-width {
    height: 100%;
    max-width: 100%;
}
</style>


