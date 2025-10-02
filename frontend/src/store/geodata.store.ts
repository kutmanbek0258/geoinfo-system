import geodataService from "@/services/geodata.service";
import type { Project, ProjectPoint, ProjectMultiline, ProjectPolygon, ImageryLayer, Page } from "@/types/api";
import type { ActionContext } from "vuex";

interface GeodataState {
    projects: Page<Project> | null;
    imageryLayers: Page<ImageryLayer> | null;
    points: ProjectPoint[];
    multilines: ProjectMultiline[];
    polygons: ProjectPolygon[];
    selectedProjectId: string | null;
    isLoading: boolean;
    error: string | null;
}

const state: GeodataState = {
    projects: null,
    imageryLayers: null,
    points: [],
    multilines: [],
    polygons: [],
    selectedProjectId: null,
    isLoading: false,
    error: null,
};

const mutations = {
    SET_PROJECTS(state: GeodataState, projects: Page<Project> | null) {
        state.projects = projects;
    },
    // Мутации для ADD/UPDATE/REMOVE теперь не нужны, т.к. мы перезапрашиваем список

    SET_VECTOR_DATA(state: GeodataState, { type, data }: { type: 'points' | 'multilines' | 'polygons', data: any[] }) {
        state[type] = data;
    },
    SET_SELECTED_PROJECT_ID(state: GeodataState, projectId: string | null) {
        state.selectedProjectId = projectId;
    },
    SET_LOADING(state: GeodataState, isLoading: boolean) {
        state.isLoading = isLoading;
    },
    SET_ERROR(state: GeodataState, error: string | null) {
        state.error = error;
    },

    // Imagery Layer Mutations
    SET_IMAGERY_LAYERS(state: GeodataState, layers: Page<ImageryLayer> | null) {
        state.imageryLayers = layers;
    },
};

const actions = {
    async fetchProjects({ commit }: ActionContext<GeodataState, any>, { page, size }: { page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geodataService.getProjects(page, size);
            commit('SET_PROJECTS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch projects.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async createProject({ dispatch }, { projectData, page, size }: { projectData: Omit<Project, 'id'>, page: number, size: number }) {
        await geodataService.createProject(projectData);
        dispatch('fetchProjects', { page, size });
    },
    async updateProject({ dispatch }, { projectData, page, size }: { projectData: Project, page: number, size: number }) {
        await geodataService.updateProject(projectData.id, projectData);
        dispatch('fetchProjects', { page, size });
    },
    async deleteProject({ dispatch }, { projectId, page, size }: { projectId: string, page: number, size: number }) {
        await geodataService.deleteProject(projectId);
        dispatch('fetchProjects', { page, size });
    },

    // Imagery Layer Actions
    async fetchImageryLayers({ commit }: ActionContext<GeodataState, any>, { page, size }: { page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geodataService.getImageryLayers(page, size);
            commit('SET_IMAGERY_LAYERS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch imagery layers.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async createImageryLayer({ dispatch }, { layerData, page, size }: { layerData: Omit<ImageryLayer, 'id'>, page: number, size: number }) {
        await geodataService.createImageryLayer(layerData);
        dispatch('fetchImageryLayers', { page, size });
    },
    async updateImageryLayer({ dispatch }, { layerData, page, size }: { layerData: ImageryLayer, page: number, size: number }) {
        await geodataService.updateImageryLayer(layerData.id, layerData);
        dispatch('fetchImageryLayers', { page, size });
    },
    async deleteImageryLayer({ dispatch }, { layerId, page, size }: { layerId: string, page: number, size: number }) {
        await geodataService.deleteImageryLayer(layerId);
        dispatch('fetchImageryLayers', { page, size });
    },

    // Vector Data Actions
    async fetchVectorDataForProject({ commit }: ActionContext<GeodataState, any>, projectId: string) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const [pointsRes, multilinesRes, polygonsRes] = await Promise.all([
                geodataService.getPointsByProjectId(projectId),
                geodataService.getMultilinesByProjectId(projectId),
                geodataService.getPolygonsByProjectId(projectId),
            ]);
            commit('SET_VECTOR_DATA', { type: 'points', data: pointsRes.data.content });
            commit('SET_VECTOR_DATA', { type: 'multilines', data: multilinesRes.data.content });
            commit('SET_VECTOR_DATA', { type: 'polygons', data: polygonsRes.data.content });
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch vector data.');
        } finally {
            commit('SET_LOADING', false);
        }
    },

    // Point Actions
    async createPoint({ dispatch, state }, pointData: Omit<ProjectPoint, 'id'>) {
        await geodataService.createPoint(pointData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async updatePoint({ dispatch, state }, pointData: ProjectPoint) {
        await geodataService.updatePoint(pointData.id, pointData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async deletePoint({ dispatch, state }, pointId: string) {
        await geodataService.deletePoint(pointId);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },

    // Multiline Actions
    async createMultiline({ dispatch, state }, multilineData: Omit<ProjectMultiline, 'id'>) {
        await geodataService.createMultiline(multilineData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async updateMultiline({ dispatch, state }, multilineData: ProjectMultiline) {
        await geodataService.updateMultiline(multilineData.id, multilineData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async deleteMultiline({ dispatch, state }, multilineId: string) {
        await geodataService.deleteMultiline(multilineId);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },

    // Polygon Actions
    async createPolygon({ dispatch, state }, polygonData: Omit<ProjectPolygon, 'id'>) {
        await geodataService.createPolygon(polygonData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async updatePolygon({ dispatch, state }, polygonData: ProjectPolygon) {
        await geodataService.updatePolygon(polygonData.id, polygonData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async deletePolygon({ dispatch, state }, polygonId: string) {
        await geodataService.deletePolygon(polygonId);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    }
};

export default {
    namespaced: true,
    state,
    mutations,
    actions,
};
