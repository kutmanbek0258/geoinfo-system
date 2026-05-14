<script setup lang="ts">
import { computed } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import MainView from './Main.vue';

const route = useRoute();
const isEditorPage = computed(() => route.name === 'OnlyOfficeEditor');
const isFullWidth = computed(() => !!route.meta?.fullWidth);

const containerClass = computed(() => {
    if (isEditorPage.value || isFullWidth.value) return 'no-padding';
    return 'page-wrapper';
});

const contentClass = computed(() => {
    if (isEditorPage.value || isFullWidth.value) return 'fullWidth';
    return 'maxWidth';
});
</script>

<template>
    <v-locale-provider >
        <v-app shadow>
            <MainView v-if="!isEditorPage" />
            <v-main>
                <v-container fluid :class="containerClass">
                    <div :class="contentClass">
                        <RouterView />
                    </div>
                </v-container>
            </v-main>
        </v-app>
    </v-locale-provider>
</template>

<style scoped>
.no-padding {
    padding: 0 !important;
    max-width: 100% !important;
    height: calc(100vh - 70px);
}
.fullWidth {
    height: 100%;
    max-width: 100% !important;
}
</style>


