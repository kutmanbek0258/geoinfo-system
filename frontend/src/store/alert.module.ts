import type { Module, Commit } from 'vuex';
import type { RootState } from './index';

export interface AlertState {
    type: 'alert-success' | 'alert-danger' | null;
    message: string | null;
}

const state: AlertState = {
    type: null,
    message: null
};

const actions = {
    success({ commit }: { commit: Commit }, message: string) {
        commit('success', message);
    },
    error({ commit }: { commit: Commit }, message: string) {
        commit('error', message);
    },
    clear({ commit }: { commit: Commit }) {
        commit('clear');
    }
};

const mutations = {
    success(state: AlertState, message: string) {
        state.type = 'alert-success';
        state.message = message;
    },
    error(state: AlertState, message: string) {
        state.type = 'alert-danger';
        state.message = message;
    },
    clear(state: AlertState) {
        state.type = null;
        state.message = null;
    }
};

export const alert: Module<AlertState, RootState> = {
    namespaced: true,
    state,
    actions,
    mutations
};
