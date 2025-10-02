import documentService from "@/services/document.service";
import type { Document } from "@/types/api";
import type { ActionContext } from "vuex";

interface DocumentState {
    documents: Document[];
    isLoading: boolean;
    error: string | null;
}

const state: DocumentState = {
    documents: [],
    isLoading: false,
    error: null,
};

const mutations = {
    SET_DOCUMENTS(state: DocumentState, documents: Document[]) {
        state.documents = documents;
    },
    ADD_DOCUMENT(state: DocumentState, document: Document) {
        state.documents.unshift(document);
    },
    REMOVE_DOCUMENT(state: DocumentState, documentId: string) {
        state.documents = state.documents.filter(doc => doc.id !== documentId);
    },
    SET_LOADING(state: DocumentState, isLoading: boolean) {
        state.isLoading = isLoading;
    },
    SET_ERROR(state: DocumentState, error: string | null) {
        state.error = error;
    },
};

const actions = {
    async fetchDocuments({ commit }: ActionContext<DocumentState, any>, geoObjectId: string) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await documentService.getDocumentsForGeoObject(geoObjectId);
            commit('SET_DOCUMENTS', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to fetch documents.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async uploadDocument({ commit }: ActionContext<DocumentState, any>, payload: { file: File, geoObjectId: string, description: string, tags: string }) {
        commit('SET_LOADING', true);
        commit('SET_ERROR', null);
        try {
            const response = await documentService.uploadDocument(payload.file, payload.geoObjectId, payload.description, payload.tags);
            commit('ADD_DOCUMENT', response.data);
        } catch (err) {
            commit('SET_ERROR', 'Failed to upload document.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async deleteDocument({ commit }: ActionContext<DocumentState, any>, documentId: string) {
        commit('SET_LOADING', true);
        try {
            await documentService.deleteDocument(documentId);
            commit('REMOVE_DOCUMENT', documentId);
        } catch (err) {
            commit('SET_ERROR', 'Failed to delete document.');
        } finally {
            commit('SET_LOADING', false);
        }
    },
    async getOnlyOfficeConfig({ commit }, { documentId, mode, userId, userName }: { documentId: string, mode: 'view' | 'edit', userId: string, userName: string }) {
        commit('SET_LOADING', true);
        try {
            const response = await documentService.getOnlyOfficeConfig(documentId, mode, userId, userName);
            return response.data; // Возвращаем конфиг напрямую компоненту
        } catch (err) {
            commit('SET_ERROR', 'Failed to get OnlyOffice config.');
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
