import api from "./api";

const API_URL = "/documents";

class DocumentService {
    /**
     * Получить список всех документов, опционально фильтруя по ID геообъекта.
     * @param {object} params - Параметры запроса, например { geoObjectId: 'uuid' }.
     */
    getAll(params) {
        return api.get(API_URL, { params });
    }

    /**
     * Получить детальную информацию по одному документу.
     * @param {string} id - UUID документа.
     */
    getById(id) {
        return api.get(`${API_URL}/${id}`);
    }

    /**
     * Создать новый документ (загрузить файл).
     * @param {File} file - Загружаемый файл.
     * @param {string} geoObjectId - UUID геообъекта, к которому привязывается документ.
     * @param {string} description - Описание документа.
     */
    create(file, geoObjectId, description) {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("geoObjectId", geoObjectId);
        formData.append("description", description);

        return api.post(API_URL, formData, {
            headers: {
                "Content-Type": "multipart/form-data",
            },
        });
    }

    /**
     * Обновить метаданные документа.
     * @param {string} id - UUID документа.
     * @param {object} data - Данные для обновления, например { description: 'новое описание' }.
     */
    update(id, data) {
        return api.put(`${API_URL}/${id}`, data);
    }

    /**
     * Удалить документ.
     * @param {string} id - UUID документа.
     */
    delete(id) {
        return api.delete(`${API_URL}/${id}`);
    }

    /**
     * Получить URL для скачивания файла.
     * @param {string} id - UUID документа.
     * @returns {string} Полный URL для скачивания.
     */
    getDownloadUrl(id) {
        return `${api.defaults.baseURL}${API_URL}/${id}/download`;
    }

    /**
     * Получить конфигурацию для редактора OnlyOffice.
     * @param {string} id - UUID документа.
     * @param {object} params - Параметры, например { mode: 'edit', userId: '...', userName: '...' }.
     */
    getOnlyOfficeConfig(id, params) {
        return api.get(`${API_URL}/${id}/onlyoffice-config`, { params });
    }
}

export default new DocumentService();
