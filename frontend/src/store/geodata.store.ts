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
    selectedFeatureId: string | null;
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
    selectedFeatureId: null,
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
    SET_SELECTED_FEATURE_ID(state: GeodataState, featureId: string | null) {
        state.selectedFeatureId = featureId;
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

    UPDATE_FEATURE(state: GeodataState, { type, data }: { type: 'Point' | 'MultiLineString' | 'Polygon', data: any }) {
        let targetArrayName: 'points' | 'multilines' | 'polygons';
        if (type === 'Point') {
            targetArrayName = 'points';
        } else if (type === 'MultiLineString') {
            targetArrayName = 'multilines';
        } else if (type === 'Polygon') {
            targetArrayName = 'polygons';
        } else {
            return;
        }
        const targetArray = state[targetArrayName];
        const index = targetArray.findIndex((f: any) => f.id === data.id);
        if (index !== -1) {
            state[targetArrayName] = [...targetArray.slice(0, index), data, ...targetArray.slice(index + 1)];
        }
    },

    DELETE_FEATURE(state: GeodataState, { type, id }: { type: 'Point' | 'MultiLineString' | 'Polygon', id: string }) {
        let targetArrayName: 'points' | 'multilines' | 'polygons';
        if (type === 'Point') {
            targetArrayName = 'points';
        } else if (type === 'MultiLineString') {
            targetArrayName = 'multilines';
        } else if (type === 'Polygon') {
            targetArrayName = 'polygons';
        } else {
            return;
        }
        const targetArray = state[targetArrayName];
        const index = targetArray.findIndex((f: any) => f.id === id);
        if (index !== -1) {
            state[targetArrayName] = [...targetArray.slice(0, index), ...targetArray.slice(index + 1)];
        }
    },

    ADD_FEATURE(state: GeodataState, { type, data }: { type: 'Point' | 'MultiLineString' | 'Polygon', data: any }) {
        let targetArrayName: 'points' | 'multilines' | 'polygons';
        if (type === 'Point') {
            targetArrayName = 'points';
        } else if (type === 'MultiLineString') {
            targetArrayName = 'multilines';
        } else if (type === 'Polygon') {
            targetArrayName = 'polygons';
        } else {
            return;
        }
        state[targetArrayName] = [...state[targetArrayName], data];
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

    async shareProject({ commit }, { projectId, email, permissionLevel }: { projectId: string, email: string, permissionLevel: string }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            await geodataService.shareProject(projectId, email, permissionLevel);
        } catch (err) {
            commit('SET_ERROR', 'Failed to share project.');
            // Re-throw the error if you want the component to know about it
            throw err;
        } finally {
            commit('SET_LOADING', false);
        }
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

    // Feature Selection
    selectFeature({ commit }: ActionContext<GeodataState, any>, featureId: string | null) {
        commit('SET_SELECTED_FEATURE_ID', featureId);
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
    },

    // Generic Feature Actions
    async updateFeature({ dispatch, state }, { id, type, data }) {
        const { name, description } = data;
        let featureData = {};
        let feature;

        switch (type) {
            case 'Point':
                feature = state.points.find(f => f.id === id);
                break;
            case 'MultiLineString':
                feature = state.multilines.find(f => f.id === id);
                break;
            case 'Polygon':
                feature = state.polygons.find(f => f.id === id);
                break;
        }

        if (!feature) {
            throw new Error(`Feature with id ${id} not found`);
        }

        featureData = { ...feature, name, description };

        switch (type) {
            case 'Point':
                await dispatch('updatePoint', featureData);
                break;
            case 'MultiLineString':
                await dispatch('updateMultiline', featureData);
                break;
            case 'Polygon':
                await dispatch('updatePolygon', featureData);
                break;
        }
    },

    // Generic Feature Actions
    async createFeature({ commit }, { type, data }) {
        let newFeature;
        switch (type) {
            case 'Point':
                newFeature = (await geodataService.createPoint(data)).data;
                break;
            case 'MultiLineString':
                newFeature = (await geodataService.createMultiline(data)).data;
                break;
            case 'Polygon':
                newFeature = (await geodataService.createPolygon(data)).data;
                break;
        }
        commit('ADD_FEATURE', { type, data: newFeature });
    },

    // Generic Feature Actions
    async updateFeature({ commit, state }, { id, type, data }) {
        const { name, description } = data;
        let featureData = {};
        let feature;

        switch (type) {
            case 'Point':
                feature = state.points.find(f => f.id === id);
                break;
            case 'MultiLineString':
                feature = state.multilines.find(f => f.id === id);
                break;
            case 'Polygon':
                feature = state.polygons.find(f => f.id === id);
                break;
        }

        if (!feature) {
            throw new Error(`Feature with id ${id} not found`);
        }

        featureData = { ...feature, name, description };

        let updatedFeature;
        switch (type) {
            case 'Point':
                updatedFeature = (await geodataService.updatePoint(id, featureData)).data;
                break;
            case 'MultiLineString':
                updatedFeature = (await geodataService.updateMultiline(id, featureData)).data;
                break;
            case 'Polygon':
                updatedFeature = (await geodataService.updatePolygon(id, featureData)).data;
                break;
        }
        commit('UPDATE_FEATURE', { type, data: updatedFeature });
    },

    async deleteFeature({ commit }, { id, type }) {
        switch (type) {
            case 'Point':
                await geodataService.deletePoint(id);
                break;
            case 'MultiLineString':
                await geodataService.deleteMultiline(id);
                break;
            case 'Polygon':
                await geodataService.deletePolygon(id);
                break;
        }
        commit('DELETE_FEATURE', { type, id });
        if (state.selectedFeatureId === id) {
            commit('SET_SELECTED_FEATURE_ID', null);
        }
    }
};

export default {
    namespaced: true,
    state,
    mutations,
    actions,
};
