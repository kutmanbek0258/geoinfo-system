import geodataService from "@/services/geodata.service";
import streamService from "@/services/stream.service";
import geoAbstractionService from "@/services/geo-abstraction.service";
import type { Project, ProjectPoint, ProjectMultiline, ProjectPolygon, ImageryLayer, TerrainLayer, TerrainJob, Page, GeoFolder, ProjectPointSummary, ProjectMultilineSummary, ProjectPolygonSummary } from "@/types/api";
import type { ActionContext } from "vuex";

interface GeodataState {
    projects: Page<Project> | null;
    folders: GeoFolder[];
    imageryLayers: Page<ImageryLayer> | null;
    terrainLayers: Page<TerrainLayer> | null;
    terrainJobs: Page<TerrainJob> | null;
    points: (ProjectPoint | ProjectPointSummary)[];
    multilines: (ProjectMultiline | ProjectMultilineSummary)[];
    polygons: (ProjectPolygon | ProjectPolygonSummary)[];
    selectedProjectId: string | null;
    selectedFeatureId: string | null;
    selectedFolderId: string | null;
    lastSelectionShouldZoom: boolean;
    isLoading: boolean;
    error: string | null;
    activeCameraStream: { geoObjectId: string, streamHlsUrl: string } | null;
}

const state: GeodataState = {
    projects: null,
    folders: [],
    imageryLayers: null,
    terrainLayers: null,
    terrainJobs: null,
    points: [],
    multilines: [],
    polygons: [],
    selectedProjectId: null,
    selectedFeatureId: null,
    selectedFolderId: null,
    lastSelectionShouldZoom: false,
    isLoading: false,
    error: null,
    activeCameraStream: null,
};

const mutations = {
    SET_PROJECTS(state: GeodataState, projects: Page<Project> | null) {
        state.projects = projects;
    },
    SET_FOLDERS(state: GeodataState, folders: GeoFolder[]) {
        state.folders = folders;
    },
    SET_VECTOR_DATA(state: GeodataState, { type, data }: { type: 'points' | 'multilines' | 'polygons', data: any[] }) {
        state[type] = data;
    },
    SET_SELECTED_PROJECT_ID(state: GeodataState, projectId: string | null) {
        state.selectedProjectId = projectId;
        state.selectedFolderId = null; // Reset folder when project changes
    },
    SET_SELECTED_FEATURE_ID(state: GeodataState, payload: { id: string | null, shouldZoom?: boolean }) {
        state.selectedFeatureId = payload.id;
        state.lastSelectionShouldZoom = !!payload.shouldZoom;
    },
    SET_SELECTED_FOLDER_ID(state: GeodataState, folderId: string | null) {
        state.selectedFolderId = folderId;
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

    SET_TERRAIN_LAYERS(state: GeodataState, layers: Page<TerrainLayer> | null) {
        state.terrainLayers = layers;
    },

    SET_TERRAIN_JOBS(state: GeodataState, jobs: Page<TerrainJob> | null) {
        state.terrainJobs = jobs;
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

    PATCH_FEATURE_VISIBILITY(state: GeodataState, { type, id, visible }: { type: 'Point' | 'MultiLineString' | 'Polygon', id: string, visible: boolean }) {
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
            const feature = targetArray[index] as any;
            const updated = {
                ...feature,
                characteristics: { ...feature.characteristics, visible }
            };
            state[targetArrayName] = [...targetArray.slice(0, index), updated, ...targetArray.slice(index + 1)];
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
        const targetArray = state[targetArrayName] as any[];
        const index = targetArray.findIndex((f: any) => f.id === id);
        if (index !== -1) {
            (state[targetArrayName] as any[]) = [...targetArray.slice(0, index), ...targetArray.slice(index + 1)];
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

    SET_ACTIVE_CAMERA_STREAM(state: GeodataState, payload: { geoObjectId: string, streamHlsUrl: string } | null) {
        state.activeCameraStream = payload;
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
    async createProject({ dispatch }: ActionContext<GeodataState, any>, { projectData, page, size }: { projectData: Omit<Project, 'id'>, page: number, size: number }) {
        await geodataService.createProject(projectData);
        dispatch('fetchProjects', { page, size });
    },
    async updateProject({ dispatch }: ActionContext<GeodataState, any>, { projectData, page, size }: { projectData: Project, page: number, size: number }) {
        await geodataService.updateProject(projectData.id, projectData);
        dispatch('fetchProjects', { page, size });
    },
    async deleteProject({ dispatch }: ActionContext<GeodataState, any>, { projectId, page, size }: { projectId: string, page: number, size: number }) {
        await geodataService.deleteProject(projectId);
        dispatch('fetchProjects', { page, size });
    },

    async importFile({ dispatch, commit }: ActionContext<GeodataState, any>, { file, projectName, page, size }: { file: File, projectName?: string, page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            await geodataService.importFile(file, projectName);
            dispatch('fetchProjects', { page, size });
        } catch (err) {
            commit('SET_ERROR', 'Failed to import file.');
            throw err;
        } finally {
            commit('SET_LOADING', false);
        }
    },

    async shareProject({ commit }: ActionContext<GeodataState, any>, { projectId, email, permissionLevel }: { projectId: string, email: string, permissionLevel: string }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            await geodataService.shareProject(projectId, email, permissionLevel);
        } catch (err) {
            commit('SET_ERROR', 'Failed to share project.');
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
            const response = await geoAbstractionService.getImageryLayers(page, size);
            commit('SET_IMAGERY_LAYERS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch imagery layers.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async createImageryLayer({ dispatch }: ActionContext<GeodataState, any>, { layerData, page, size }: { layerData: Omit<ImageryLayer, 'id'>, page: number, size: number }) {
        await geoAbstractionService.createImageryLayer(layerData);
        dispatch('fetchImageryLayers', { page, size });
    },
    async updateImageryLayer({ dispatch }: ActionContext<GeodataState, any>, { layerData, page, size }: { layerData: ImageryLayer, page: number, size: number }) {
        await geoAbstractionService.updateImageryLayer(layerData.id, layerData);
        dispatch('fetchImageryLayers', { page, size });
    },
    async deleteImageryLayer({ dispatch }: ActionContext<GeodataState, any>, { layerId, page, size }: { layerId: string, page: number, size: number }) {
        await geoAbstractionService.deleteImageryLayer(layerId);
        dispatch('fetchImageryLayers', { page, size });
    },

    // Terrain Layer Actions
    async fetchTerrainLayers({ commit }: ActionContext<GeodataState, any>, { page, size }: { page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geoAbstractionService.getLayers(page, size);
            commit('SET_TERRAIN_LAYERS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch terrain layers.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async deleteTerrainLayer({ dispatch }: ActionContext<GeodataState, any>, { layerId, page, size }: { layerId: string, page: number, size: number }) {
        await geoAbstractionService.deleteLayer(layerId);
        dispatch('fetchTerrainLayers', { page, size });
    },

    async fetchTerrainJobs({ commit }: ActionContext<GeodataState, any>, { page, size }: { page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geoAbstractionService.getJobs(page, size);
            commit('SET_TERRAIN_JOBS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch terrain jobs.');
        } finally {
            commit('SET_LOADING', false);
        }
    },

    // Feature Selection
    async selectFeature({ commit, state, dispatch }: ActionContext<GeodataState, any>, payload: string | { id: string | null, shouldZoom?: boolean } | null) {
        let id: string | null;
        let shouldZoom = true;

        if (typeof payload === 'string' || payload === null) {
            id = payload;
        } else {
            id = payload.id;
            shouldZoom = payload.shouldZoom ?? true;
        }

        commit('SET_SELECTED_FEATURE_ID', { id, shouldZoom });

        if (id) {
            // Check if we have full geometry. If not, fetch full data.
            const feature = [...state.points, ...state.multilines, ...state.polygons].find((f: any) => f.id === id);
            if (feature && !(feature as any).geom) {
                await dispatch('fetchFullFeature', { id, type: (feature as any).type || 'Point' }); // type might be missing in summary
            }
        }
    },

    async fetchFullFeature({ commit, state }: ActionContext<GeodataState, any>, { id, type }: { id: string, type: string }) {
        try {
            let response;
            let typeForMutation: 'Point' | 'MultiLineString' | 'Polygon' = 'Point';
            
            // We might not know the type yet if it's from a generic summary
            // Try to find it in summaries first to guess type
            // Actually, DTOs for points/lines/polygons are different, so we should know from which array it came.
            
            // For now, let's assume we can try all 3 or the caller knows.
            // If caller doesn't know, we can check which array it's in.
            
            // Better: GeodataService.getPointById etc.
            // Let's try to detect type from state
            const isPoint = state.points.some((f: any) => f.id === id);
            const isLine = state.multilines.some((f: any) => f.id === id);
            const isPoly = state.polygons.some((f: any) => f.id === id);

            if (isPoint) {
                response = await geodataService.getPointById(id);
                typeForMutation = 'Point';
            } else if (isLine) {
                response = await geodataService.getMultilineById(id);
                typeForMutation = 'MultiLineString';
            } else if (isPoly) {
                response = await geodataService.getPolygonById(id);
                typeForMutation = 'Polygon';
            }

            if (response) {
                commit('UPDATE_FEATURE', { type: typeForMutation, data: response.data });
            }
        } catch (err) {
            console.error('Failed to fetch full feature data:', err);
        }
    },

    // Vector Data Actions
    async fetchVectorDataForProject({ commit, dispatch }: ActionContext<GeodataState, any>, projectId: string) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            await dispatch('fetchFolders', projectId);
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

    async fetchVectorSummaryForProject({ commit, dispatch }: ActionContext<GeodataState, any>, projectId: string) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            await dispatch('fetchFolders', projectId);
            const [pointsRes, multilinesRes, polygonsRes] = await Promise.all([
                geodataService.getPointsSummaryByProjectId(projectId),
                geodataService.getMultilinesSummaryByProjectId(projectId),
                geodataService.getPolygonsSummaryByProjectId(projectId),
            ]);
            commit('SET_VECTOR_DATA', { type: 'points', data: pointsRes.data.content });
            commit('SET_VECTOR_DATA', { type: 'multilines', data: multilinesRes.data.content });
            commit('SET_VECTOR_DATA', { type: 'polygons', data: polygonsRes.data.content });
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch vector summary data.');
        } finally {
            commit('SET_LOADING', false);
        }
    },

    // Folder Actions
    async fetchFolders({ commit }: ActionContext<GeodataState, any>, projectId: string) {
        const response = await geodataService.getFoldersByProjectId(projectId);
        commit('SET_FOLDERS', response.data);
    },
    async createFolder({ dispatch, state }: ActionContext<GeodataState, any>, folderData: Omit<GeoFolder, 'id'>) {
        await geodataService.createFolder(folderData);
        if (state.selectedProjectId) {
            dispatch('fetchFolders', state.selectedProjectId);
        }
    },
    async updateFolder({ dispatch, state }: ActionContext<GeodataState, any>, folderData: GeoFolder) {
        await geodataService.updateFolder(folderData.id, folderData);
        if (state.selectedProjectId) {
            dispatch('fetchFolders', state.selectedProjectId);
        }
    },
    async deleteFolder({ dispatch, state }: ActionContext<GeodataState, any>, folderId: string) {
        await geodataService.deleteFolder(folderId);
        if (state.selectedProjectId) {
            dispatch('fetchFolders', state.selectedProjectId);
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },

    // Point Actions
    async createPoint({ dispatch, state }: ActionContext<GeodataState, any>, pointData: Omit<ProjectPoint, 'id'>) {
        await geodataService.createPoint(pointData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async updatePoint({ dispatch, state }: ActionContext<GeodataState, any>, pointData: ProjectPoint) {
        await geodataService.updatePoint(pointData.id, pointData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async deletePoint({ dispatch, state }: ActionContext<GeodataState, any>, pointId: string) {
        await geodataService.deletePoint(pointId);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },

    // Multiline Actions
    async createMultiline({ dispatch, state }: ActionContext<GeodataState, any>, multilineData: Omit<ProjectMultiline, 'id'>) {
        await geodataService.createMultiline(multilineData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async updateMultiline({ dispatch, state }: ActionContext<GeodataState, any>, multilineData: ProjectMultiline) {
        await geodataService.updateMultiline(multilineData.id, multilineData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async deleteMultiline({ dispatch, state }: ActionContext<GeodataState, any>, multilineId: string) {
        await geodataService.deleteMultiline(multilineId);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },

    // Polygon Actions
    async createPolygon({ dispatch, state }: ActionContext<GeodataState, any>, polygonData: Omit<ProjectPolygon, 'id'>) {
        await geodataService.createPolygon(polygonData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async updatePolygon({ dispatch, state }: ActionContext<GeodataState, any>, polygonData: ProjectPolygon) {
        await geodataService.updatePolygon(polygonData.id, polygonData);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },
    async deletePolygon({ dispatch, state }: ActionContext<GeodataState, any>, polygonId: string) {
        await geodataService.deletePolygon(polygonId);
        if (state.selectedProjectId) {
            dispatch('fetchVectorDataForProject', state.selectedProjectId);
        }
    },

    // Generic Feature Actions
    async createFeature({ commit }: ActionContext<GeodataState, any>, { type, data }: { type: string, data: any }) {
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
    async updateFeature({ commit, state }: ActionContext<GeodataState, any>, { id, type, data }: { id: string, type: string, data: { name: string, description: string, geom?: any } }) {
        let feature;

        switch (type) {
            case 'Point':
                feature = state.points.find((f: any) => f.id === id);
                break;
            case 'MultiLineString':
                feature = state.multilines.find((f: any) => f.id === id);
                break;
            case 'Polygon':
                feature = state.polygons.find((f: any) => f.id === id);
                break;
        }

        if (!feature) {
            throw new Error(`Feature with id ${id} not found`);
        }

        const featureData = { ...feature, ...data };

        let updatedFeature;
        switch (type) {
            case 'Point':
                updatedFeature = (await geodataService.updatePoint(id, featureData as ProjectPoint)).data;
                break;
            case 'MultiLineString':
                updatedFeature = (await geodataService.updateMultiline(id, featureData as ProjectMultiline)).data;
                break;
            case 'Polygon':
                updatedFeature = (await geodataService.updatePolygon(id, featureData as ProjectPolygon)).data;
                break;
        }
        commit('UPDATE_FEATURE', { type, data: updatedFeature });
    },

    // Optimistic visibility toggle — updates local store instantly, then syncs with server
    async toggleFeatureVisibility({ commit, state }: ActionContext<GeodataState, any>, { id, type }: { id: string, type: string }) {
        let targetArrayName: 'points' | 'multilines' | 'polygons';
        if (type === 'Point') targetArrayName = 'points';
        else if (type === 'MultiLineString') targetArrayName = 'multilines';
        else if (type === 'Polygon') targetArrayName = 'polygons';
        else return;

        const feature = (state[targetArrayName] as any[]).find((f: any) => f.id === id);
        if (!feature) return;

        const currentVisible = feature.characteristics?.visible !== false;
        const newVisible = !currentVisible;

        // 1. Мгновенно обновляем стор (оптимистично)
        commit('PATCH_FEATURE_VISIBILITY', { type: type as 'Point' | 'MultiLineString' | 'Polygon', id, visible: newVisible });

        // 2. Отправляем запрос на сервер в фоне
        try {
            const newCharacteristics = { ...feature.characteristics, visible: newVisible };
            const payload = { ...feature, characteristics: newCharacteristics };
            switch (type) {
                case 'Point':
                    await geodataService.updatePoint(id, payload as ProjectPoint);
                    break;
                case 'MultiLineString':
                    await geodataService.updateMultiline(id, payload as ProjectMultiline);
                    break;
                case 'Polygon':
                    await geodataService.updatePolygon(id, payload as ProjectPolygon);
                    break;
            }
        } catch (err) {
            // Откат при ошибке
            commit('PATCH_FEATURE_VISIBILITY', { type: type as 'Point' | 'MultiLineString' | 'Polygon', id, visible: currentVisible });
            console.error('Failed to toggle feature visibility, reverting:', err);
        }
    },

    async deleteFeature({ commit, state }: ActionContext<GeodataState, any>, { id, type }: { id: string, type: string }) {
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
    },

    async uploadMainImage({ commit }: ActionContext<GeodataState, any>, { objectType, objectId, file }: { objectType: 'points' | 'multilines' | 'polygons', objectId: string, file: File }) {
        let typeForMutation: 'Point' | 'MultiLineString' | 'Polygon';
        switch (objectType) {
            case 'points': typeForMutation = 'Point'; break;
            case 'multilines': typeForMutation = 'MultiLineString'; break;
            case 'polygons': typeForMutation = 'Polygon'; break;
        }

        const response = await geodataService.uploadMainImage(objectType, objectId, file);
        commit('UPDATE_FEATURE', { type: typeForMutation, data: response.data });
    },

    // Stream Actions
    async startCameraStream({ commit, dispatch }: ActionContext<GeodataState, any>, geoObjectId: string) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const streamHlsUrl = await streamService.startStream(geoObjectId);
            commit('SET_ACTIVE_CAMERA_STREAM', { geoObjectId, streamHlsUrl });
        } catch (err) {
            commit('SET_ERROR', 'Failed to start camera stream.');
            dispatch('alert/error', 'Не удалось запустить трансляцию с камеры. Проверьте подключение или обратитесь к администратору.', { root: true });
        } finally {
            commit('SET_LOADING', false);
        }
    },

    async stopCameraStream({ commit }: ActionContext<GeodataState, any>, geoObjectId: string) {
        try {
            await streamService.stopStream(geoObjectId);
            commit('SET_ACTIVE_CAMERA_STREAM', null);
        } catch (err) {
            console.error('Failed to stop camera stream:', err);
        }
    },
};

export default {
    namespaced: true,
    state,
    mutations,
    actions,
};
