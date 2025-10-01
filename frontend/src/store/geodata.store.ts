import geodataService from "@/services/geodata.service";
import type { Project, ProjectPoint, ProjectMultiline, ProjectPolygon } from "@/types/api";
import type { ActionContext } from "vuex";

interface GeodataState {
    projects: Project[];
    points: ProjectPoint[];
    multilines: ProjectMultiline[];
    polygons: ProjectPolygon[];
    selectedProjectId: string | null;
    isLoading: boolean;
    error: string | null;
}

const state: GeodataState = {
    projects: [],
    points: [],
    multilines: [],
    polygons: [],
    selectedProjectId: null,
    isLoading: false,
    error: null,
};

const mutations = {
    SET_PROJECTS(state: GeodataState, projects: Project[]) {
        state.projects = projects;
    },
    ADD_PROJECT(state: GeodataState, project: Project) {
        state.projects.unshift(project);
    },
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
};

const actions = {
    async fetchProjects({ commit }: ActionContext<GeodataState, any>) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await geodataService.getProjects();
            commit('SET_PROJECTS', response.data.content);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch projects.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async createProject({ commit }: ActionContext<GeodataState, any>, projectData: Omit<Project, 'id'>) {
        // ... реализация с вызовом geodataService.createProject и мутациями
    },
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
    }
};

export default {
    namespaced: true,
    state,
    mutations,
    actions,
};
