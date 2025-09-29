import DocumentService from '../services/document.service';

export const documentModule = {
    namespaced: true,
    state: {
        documents: [], // Список документов для геообъекта
        document: null,  // Детальная информация по одному документу
        status: {
            isLoading: false,
            isError: false,
            errorMessage: ''
        },
    },
    actions: {
        // Получить все документы (например, для конкретного геообъекта)
        fetchAll({ commit }, params) {
            commit('setRequestStatus', { isLoading: true, isError: false });
            return DocumentService.getAll(params).then(
                response => {
                    commit('setDocumentsSuccess', response.data);
                    return Promise.resolve(response.data);
                },
                error => {
                    commit('setRequestStatus', { isLoading: false, isError: true, errorMessage: error.message });
                    return Promise.reject(error);
                }
            );
        },

        // Создать новый документ и обновить список
        create({ dispatch }, { file, geoObjectId, description }) {
            return DocumentService.create(file, geoObjectId, description).then(
                () => {
                    // После успешного создания обновляем список документов для этого геообъекта
                    return dispatch('fetchAll', { geoObjectId });
                },
                error => {
                    return Promise.reject(error);
                }
            );
        },

        // Удалить документ и обновить список
        delete({ dispatch }, { id, geoObjectId }) {
            return DocumentService.delete(id).then(
                () => {
                    // После успешного удаления обновляем список
                    return dispatch('fetchAll', { geoObjectId });
                },
                error => {
                    return Promise.reject(error);
                }
            );
        },

        // Получить конфигурацию для OnlyOffice (не сохраняем в state)
        fetchOnlyOfficeConfig(_, { id, params }) {
            return DocumentService.getOnlyOfficeConfig(id, params).then(
                response => Promise.resolve(response.data),
                error => Promise.reject(error)
            );
        }
    },
    mutations: {
        setRequestStatus(state, { isLoading, isError, errorMessage = '' }) {
            state.status.isLoading = isLoading;
            state.status.isError = isError;
            state.status.errorMessage = errorMessage;
        },
        setDocumentsSuccess(state, documents) {
            state.documents = documents;
            state.status.isLoading = false;
            state.status.isError = false;
        },
        setDocumentSuccess(state, document) {
            state.document = document;
            state.status.isLoading = false;
            state.status.isError = false;
        }
    },
    getters: {
        getDocuments: (state) => state.documents,
        isLoading: (state) => state.status.isLoading,
    }
};
