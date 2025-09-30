import GeodataService from '../services/geodata.service';

export const geodataModule = {
    namespaced: true,
    state: {
        projects: [],
        selectedProject: null,
        projectFeatures: {
            points: [],
            lines: [],
            polygons: []
        },
        imageryLayers: [],
        status: {
            isLoading: false,
            isError: false,
        },
    },
    actions: {
        // --- Projects ---
        fetchProjects({ commit }) {
            commit('setRequestStatus', { isLoading: true });
            return GeodataService.getProjects().then(
                response => {
                    commit('setProjectsSuccess', response.data);
                    return Promise.resolve(response.data);
                },
                error => {
                    commit('setRequestStatus', { isLoading: false, isError: true });
                    return Promise.reject(error);
                }
            );
        },

        // --- Features for a selected project ---
        fetchProjectDetails({ commit }, projectId) {
            commit('setRequestStatus', { isLoading: true });
            const projectRequest = GeodataService.getProjectById(projectId);
            const pointsRequest = GeodataService.getFeaturesForProject(projectId, 'points');
            const linesRequest = GeodataService.getFeaturesForProject(projectId, 'lines');
            const polygonsRequest = GeodataService.getFeaturesForProject(projectId, 'polygons');

            return Promise.all([projectRequest, pointsRequest, linesRequest, polygonsRequest]).then(
                ([projectRes, pointsRes, linesRes, polygonsRes]) => {
                    commit('setSelectedProjectSuccess', projectRes.data);
                    commit('setProjectFeaturesSuccess', {
                        points: pointsRes.data,
                        lines: linesRes.data,
                        polygons: polygonsRes.data
                    });
                    return Promise.resolve();
                },
                error => {
                    commit('setRequestStatus', { isLoading: false, isError: true });
                    return Promise.reject(error);
                }
            );
        },

        // --- Create a new feature and refresh ---
        createFeature({ dispatch }, { projectId, featureType, data }) {
            return GeodataService.createFeature(projectId, featureType, data).then(() => {
                // Refresh the data for the current project
                return dispatch('fetchProjectDetails', projectId);
            });
        },

        // --- Imagery Layers ---
        fetchImageryLayers({ commit }) {
            commit('setRequestStatus', { isLoading: true });
            return GeodataService.getImageryLayers().then(
                response => {
                    commit('setImageryLayersSuccess', response.data);
                    return Promise.resolve(response.data);
                },
                error => {
                    commit('setRequestStatus', { isLoading: false, isError: true });
                    return Promise.reject(error);
                }
            );
        }
    },
    mutations: {
        setRequestStatus(state, { isLoading, isError = false }) {
            state.status.isLoading = isLoading;
            state.status.isError = isError;
        },
        setProjectsSuccess(state, projects) {
            state.projects = projects;
            state.status.isLoading = false;
        },
        setSelectedProjectSuccess(state, project) {
            state.selectedProject = project;
        },
        setProjectFeaturesSuccess(state, features) {
            state.projectFeatures = features;
            state.status.isLoading = false;
        },
        setImageryLayersSuccess(state, layers) {
            state.imageryLayers = layers;
            state.status.isLoading = false;
        }
    },
    getters: {
        allProjects: (state) => state.projects,
        currentProject: (state) => state.selectedProject,
        currentFeatures: (state) => state.projectFeatures,
        allImageryLayers: (state) => state.imageryLayers,
        isLoading: (state) => state.status.isLoading,
    }
};
