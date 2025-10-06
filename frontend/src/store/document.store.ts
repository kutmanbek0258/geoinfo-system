import documentService from "@/services/document.service";
import type { Document } from "@/types/api";
import type { ActionContext } from "vuex";

interface DocumentState {
    documents: Document[];
    isLoading: boolean;
    isUploading: boolean;
    error: string | null;
}

const state: DocumentState = {
    documents: [],
    isLoading: false,
    isUploading: false,
    error: null,
};

const mutations = {
    SET_DOCUMENTS(state: DocumentState, documents: Document[]) {
        state.documents = documents;
    },
    SET_LOADING(state: DocumentState, isLoading: boolean) {
        state.isLoading = isLoading;
    },
    SET_UPLOADING(state: DocumentState, isUploading: boolean) {
        state.isUploading = isUploading;
    },
    SET_ERROR(state: DocumentState, error: string | null) {
        state.error = error;
    },
    ADD_DOCUMENT(state: DocumentState, document: Document) {
        state.documents.push(document);
    },
    REMOVE_DOCUMENT(state: DocumentState, documentId: string) {
        state.documents = state.documents.filter(doc => doc.id !== documentId);
    }
};

const actions = {
    async fetchDocumentsForObject({ commit }: ActionContext<DocumentState, any>, geoObjectId: string | null) {
        if (!geoObjectId) {
            commit('SET_DOCUMENTS', []);
            return;
        }
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await documentService.getDocumentsByGeoObjectId(geoObjectId);
            commit('SET_DOCUMENTS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch documents.');
            commit('SET_DOCUMENTS', []);
        } finally {
            commit('SET_LOADING', false);
        }
    },

    async uploadDocument({ commit, dispatch }, { file, geoObjectId, description, tags }: { file: File, geoObjectId: string, description?: string, tags?: string }) {
        commit('SET_UPLOADING', true);
        commit('SET_ERROR', null);
        try {
            await documentService.uploadDocument(geoObjectId, file, description, tags);
            // После успешной загрузки обновляем список
            dispatch('fetchDocumentsForObject', geoObjectId);
        } catch (err) {
            commit('SET_ERROR', 'Failed to upload document.');
        } finally {
            commit('SET_UPLOADING', false);
        }
    },

    async deleteDocument({ commit, dispatch }, { docId, geoObjectId }: { docId: string, geoObjectId: string }) {
        commit('SET_LOADING', true); // Можно использовать общий лоадер
        commit('SET_ERROR', null);
        try {
            await documentService.deleteDocument(docId);
            // После успешного удаления обновляем список
            dispatch('fetchDocumentsForObject', geoObjectId);
        } catch (err) {
            commit('SET_ERROR', 'Failed to delete document.');
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