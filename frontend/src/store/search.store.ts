import searchService from "@/services/search.service";
import type { Page, SearchResult } from "@/types/api";
import type { ActionContext } from "vuex";

interface SearchState {
    results: Page<SearchResult> | null;
    isLoading: boolean;
    error: string | null;
}

const state: SearchState = {
    results: null,
    isLoading: false,
    error: null,
};

const mutations = {
    SET_RESULTS(state: SearchState, results: Page<SearchResult> | null) {
        state.results = results;
    },
    CLEAR_RESULTS(state: SearchState) {
        state.results = null;
    },
    SET_LOADING(state: SearchState, isLoading: boolean) {
        state.isLoading = isLoading;
    },
    SET_ERROR(state: SearchState, error: string | null) {
        state.error = error;
    },
};

const actions = {
    async performSearch({ commit }: ActionContext<SearchState, any>, { query, page, size }: { query: string, page?: number, size?: number }) {
        if (!query) {
            commit('CLEAR_RESULTS');
            return;
        }
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await searchService.search(query, page, size);
            commit('SET_RESULTS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Search request failed.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
};

export default {
    namespaced: true,
    state,
    mutations,
    actions,
};
