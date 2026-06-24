import geodataService from "@/services/geodata.service";
import streamService from "@/services/stream.service";
import geoAbstractionService from "@/services/geo-abstraction.service";
import type { Project, ProjectPoint, ProjectMultiline, ProjectPolygon, ImageryLayer, TerrainLayer, TerrainJob, Page, GeoFolder, ProjectPointSummary, ProjectMultilineSummary, ProjectPolygonSummary, AnalysisTask, CreateAnalysisTaskDto } from "@/types/api";
import type { ActionContext } from "vuex";

interface StagingLayer {
    taskId: string;
    type: 'VECTOR' | 'RASTER';
    url: string;           // MVT tile URL or presigned COG URL
    s3Url?: string;        // Raw S3 URL for TiTiler
    interpolation?: string; // Interpolation style (e.g. 'bilinear')
    colormap?: string;     // TiTiler colormap string
    styleId?: string | null; // Selected RasterStyle ID
    pluginName: string;
    label: string;
}

interface GeodataState {
    projects: Page<Project> | null;
    currentProject: Project | null;
    folders: GeoFolder[];
    imageryLayers: Page<ImageryLayer> | null;
    terrainLayers: Page<TerrainLayer> | null;
    terrainJobs: Page<TerrainJob> | null;
    analysisTasks: AnalysisTask[];
    stagingLayers: StagingLayer[];
    points: (ProjectPoint | ProjectPointSummary)[];
    multilines: (ProjectMultiline | ProjectMultilineSummary)[];
    polygons: (ProjectPolygon | ProjectPolygonSummary)[];
    selectedProjectId: string | null;
    selectedFeatureId: string | null;
    selectedFolderId: string | null;
    lastSelectionSource: 'map' | 'list' | null;
    initialZoomDone: boolean;
    isLoading: boolean;
    error: string | null;
    activeCameraStream: { geoObjectId: string, streamHlsUrl: string } | null;
    pointSelectionActive: boolean;
    selectedPoint: { x: number, y: number } | null;
}

const state: GeodataState = {
    projects: null,
    currentProject: null,
    folders: [],
    imageryLayers: null,
    terrainLayers: null,
    terrainJobs: null,
    analysisTasks: [],
    stagingLayers: [],
    points: [],
    multilines: [],
    polygons: [],
    selectedProjectId: null,
    selectedFeatureId: null,
    selectedFolderId: null,
    lastSelectionSource: null,
    initialZoomDone: false,
    isLoading: false,
    error: null,
    activeCameraStream: null,
    pointSelectionActive: false,
    selectedPoint: null,
};

const mutations = {
    SET_POINT_SELECTION_ACTIVE(state: GeodataState, active: boolean) {
        state.pointSelectionActive = active;
    },
    SET_SELECTED_POINT(state: GeodataState, point: { x: number, y: number } | null) {
        state.selectedPoint = point;
    },
    SET_PROJECTS(state: GeodataState, projects: Page<Project> | null) {
        state.projects = projects;
    },
    SET_CURRENT_PROJECT(state: GeodataState, project: Project | null) {
        state.currentProject = project;
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
        state.initialZoomDone = false; // Reset initial zoom for new project
    },
    SET_SELECTED_FEATURE_ID(state: GeodataState, payload: { id: string | null, source?: 'map' | 'list' }) {
        state.selectedFeatureId = payload.id;
        state.lastSelectionSource = payload.source ?? null;
    },
    SET_INITIAL_ZOOM_DONE(state: GeodataState, done: boolean) {
        state.initialZoomDone = done;
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

    SET_ANALYSIS_TASKS(state: GeodataState, tasks: AnalysisTask[]) {
        state.analysisTasks = tasks;
    },

    UPDATE_ANALYSIS_TASK(state: GeodataState, task: AnalysisTask) {
        const index = state.analysisTasks.findIndex(t => t.id === task.id);
        if (index !== -1) {
            state.analysisTasks = [...state.analysisTasks.slice(0, index), task, ...state.analysisTasks.slice(index + 1)];
        } else {
            state.analysisTasks = [...state.analysisTasks, task];
        }
    },

    ADD_STAGING_LAYER(state: GeodataState, layer: StagingLayer) {
        // Replace previous layer from same task if re-run
        state.stagingLayers = [
            ...state.stagingLayers.filter(l => l.taskId !== layer.taskId),
            layer
        ];
    },

    REMOVE_STAGING_LAYER(state: GeodataState, taskId: string) {
        state.stagingLayers = state.stagingLayers.filter(l => l.taskId !== taskId);
    },

    UPDATE_STAGING_LAYER_INTERPOLATION(state: GeodataState, { taskId, interpolation }: { taskId: string, interpolation: string }) {
        state.stagingLayers = state.stagingLayers.map(l => {
            if (l.taskId === taskId) {
                return { ...l, interpolation };
            }
            return l;
        });
    },

    UPDATE_STAGING_LAYER_COLORMAP(state: GeodataState, { taskId, colormap, styleId }: { taskId: string, colormap: string, styleId: string | null }) {
        state.stagingLayers = state.stagingLayers.map(l => {
            if (l.taskId === taskId) {
                return { ...l, colormap, styleId };
            }
            return l;
        });
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
    async fetchProject({ commit }: ActionContext<GeodataState, any>, projectId: string) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geodataService.getProjectById(projectId);
            commit('SET_CURRENT_PROJECT', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch project details.');
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
    async fetchImageryLayers({ commit, state }: ActionContext<GeodataState, any>, { page, size }: { page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geoAbstractionService.getImageryLayers(page, size, state.selectedProjectId || undefined);
            commit('SET_IMAGERY_LAYERS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch imagery layers.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async createImageryLayer({ dispatch, state }: ActionContext<GeodataState, any>, { layerData, page, size }: { layerData: Omit<ImageryLayer, 'id'>, page: number, size: number }) {
        const payload = { ...layerData, projectId: state.selectedProjectId || undefined };
        await geoAbstractionService.createImageryLayer(payload as ImageryLayer);
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
    async fetchTerrainLayers({ commit, state }: ActionContext<GeodataState, any>, { page, size }: { page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geoAbstractionService.getLayers(page, size, state.selectedProjectId || undefined);
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

    async fetchAnalysisTasksByProject({ commit }: ActionContext<GeodataState, any>, projectId: string) {
        try {
            const response = await geoAbstractionService.getAnalysisTasksByProject(projectId);
            const tasks = response.data;
            console.log(tasks);
            commit('SET_ANALYSIS_TASKS', tasks);
            
            // Populate staging layers for completed tasks
            tasks.forEach(task => {
                if (task.status === 'COMPLETED') {
                    if (task.s3OutputPaths?.vector_result) {
                        const tileUrl = `/tiles/geodata.get_staging_layer/{z}/{x}/{y}.pbf?task_uuid=${task.id}`;
                        commit('ADD_STAGING_LAYER', {
                            taskId: task.id,
                            type: 'VECTOR',
                            url: tileUrl,
                            pluginName: task.pluginName,
                            label: `[Анализ] ${task.pluginName} (${task.id.slice(0, 8)})`
                        } as StagingLayer);
                    }
                    if (task.s3OutputPaths?.raster_result) {
                        commit('ADD_STAGING_LAYER', {
                            taskId: task.id,
                            type: 'RASTER',
                            url: '',
                            s3Url: task.s3OutputPaths.raster_result,
                            interpolation: 'bilinear',
                            pluginName: task.pluginName,
                            label: `[Растр] ${task.pluginName} (${task.id.slice(0, 8)})`
                        } as StagingLayer);
                    }
                }
            });
        } catch (err) {
            console.error('Failed to fetch analysis tasks:', err);
        }
    },

    // Analysis Actions
    async triggerAnalysis({ commit, dispatch }: ActionContext<GeodataState, any>, dto: CreateAnalysisTaskDto) {
        commit('SET_LOADING', true);
        try {
            const response = await geoAbstractionService.createAnalysisTask(dto);
            commit('UPDATE_ANALYSIS_TASK', response.data);
            
            // Start polling for this task
            dispatch('pollAnalysisTask', response.data.id);
            return response.data;
        } catch (err) {
            commit('SET_ERROR', 'Failed to trigger analysis.');
            throw err;
        } finally {
            commit('SET_LOADING', false);
        }
    },

    async pollAnalysisTask({ commit, dispatch }: ActionContext<GeodataState, any>, taskId: string) {
        try {
            const response = await geoAbstractionService.getAnalysisTask(taskId);
            const task = response.data;
            console.log(task);
            commit('UPDATE_ANALYSIS_TASK', task);

            if (task.status === 'PENDING' || task.status === 'PROCESSING') {
                setTimeout(() => dispatch('pollAnalysisTask', taskId), 3000);
            } else if (task.status === 'COMPLETED') {
                // Mount vector staging layer via pg_tileserv RPC if worker produced vector output
                if (task.s3OutputPaths?.vector_result) {
                    const tileUrl = `/tiles/geodata.get_staging_layer/{z}/{x}/{y}.pbf?task_uuid=${task.id}`;
                    commit('ADD_STAGING_LAYER', {
                        taskId: task.id,
                        type: 'VECTOR',
                        url: tileUrl,
                        pluginName: task.pluginName,
                        label: `[Анализ] ${task.pluginName} (${task.id.slice(0, 8)})`
                    } as StagingLayer);
                    dispatch('alert/success', `Анализ «${task.pluginName}» завершён. Векторный слой загружен.`, { root: true });
                }
                // For raster results — fetch presigned URL and mount as RASTER staging layer
                if (task.s3OutputPaths?.raster_result) {
                    commit('ADD_STAGING_LAYER', {
                        taskId: task.id,
                        type: 'RASTER',
                        url: '',
                        s3Url: task.s3OutputPaths.raster_result,
                        interpolation: 'bilinear',
                        pluginName: task.pluginName,
                        label: `[Растр] ${task.pluginName} (${task.id.slice(0, 8)})`
                    } as StagingLayer);
                    dispatch('alert/success', `Анализ «${task.pluginName}» завершён. Растровый слой загружен.`, { root: true });
                }
                
                // General success notice if no map layers produced
                if (!task.s3OutputPaths?.vector_result && !task.s3OutputPaths?.raster_result) {
                    dispatch('alert/success', `Анализ «${task.pluginName}» завершён.`, { root: true });
                }
            } else if (task.status === 'FAILED') {
                dispatch('alert/error', `Ошибка анализа: ${task.errorMessage}`, { root: true });
            }
        } catch (err) {
            console.error('Polling error:', err);
        }
    },

    removeStagingLayer({ commit }: ActionContext<GeodataState, any>, taskId: string) {
        commit('REMOVE_STAGING_LAYER', taskId);
    },

    async commitStagingLayer({ commit, dispatch }: ActionContext<GeodataState, any>, { taskId, projectId, folderId, taskName }: { taskId: string, projectId: string, folderId: string | null, taskName: string }) {
        commit('SET_LOADING', true);
        try {
            await geoAbstractionService.commitAnalysisTask(taskId, projectId, folderId, taskName);
            commit('REMOVE_STAGING_LAYER', taskId);
            dispatch('alert/success', 'Результаты анализа успешно сохранены в проект.', { root: true });
            
            // Refresh vector geometries
            dispatch('fetchVectorSummaryForProject', projectId);
            // Refresh analysis tasks list
            dispatch('fetchAnalysisTasksByProject', projectId);
        } catch (err: any) {
            console.error(err);
            dispatch('alert/error', 'Не удалось сохранить результаты: ' + (err.response?.data?.message || err.message), { root: true });
        } finally {
            commit('SET_LOADING', false);
        }
    },

    async rejectStagingLayer({ commit, dispatch }: ActionContext<GeodataState, any>, { taskId, projectId }: { taskId: string, projectId: string }) {
        commit('SET_LOADING', true);
        try {
            await geoAbstractionService.rejectAnalysisTask(taskId);
            commit('REMOVE_STAGING_LAYER', taskId);
            dispatch('alert/success', 'Результаты анализа успешно отклонены.', { root: true });
            
            // Refresh analysis tasks list
            dispatch('fetchAnalysisTasksByProject', projectId);
        } catch (err: any) {
            console.error(err);
            dispatch('alert/error', 'Не удалось отклонить результаты: ' + (err.response?.data?.message || err.message), { root: true });
        } finally {
            commit('SET_LOADING', false);
        }
    },


    async fetchTerrainJobs({ commit, state }: ActionContext<GeodataState, any>, { page, size }: { page: number, size: number }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geoAbstractionService.getJobs(page, size, state.selectedProjectId || undefined);
            commit('SET_TERRAIN_JOBS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch terrain jobs.');
        } finally {
            commit('SET_LOADING', false);
        }
    },

    // Feature Selection
    async selectFeature({ commit, state, dispatch }: ActionContext<GeodataState, any>, payload: string | { id: string | null, source?: 'map' | 'list' } | null) {
        let id: string | null;
        let source: 'map' | 'list' | undefined;

        if (typeof payload === 'string' || payload === null) {
            id = payload;
        } else {
            id = payload.id;
            source = payload.source;
        }

        commit('SET_SELECTED_FEATURE_ID', { id, source });

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
        if (!projectId) return;
        try {
            const response = await geodataService.getFoldersByProjectId(projectId);
            commit('SET_FOLDERS', Array.isArray(response.data) ? response.data : []);
        } catch (err) {
            console.error('Failed to fetch folders:', err);
            commit('SET_FOLDERS', []);
        }
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
            commit('SET_SELECTED_FEATURE_ID', { id: null });
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
