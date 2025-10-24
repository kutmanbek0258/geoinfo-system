import api from './api';
import type { Document } from '@/types/api';

class DocumentService {

  getDocumentsByGeoObjectId(geoObjectId: string) {
    if (!geoObjectId) {
        return Promise.resolve({ data: [] }); // Возвращаем пустой массив если нет ID
    }
    return api.get<Document[]>(`/documents/geo/${geoObjectId}`);
  }

  uploadDocument(geoObjectId: string, file: File, description?: string, tags?: string) {
    const formData = new FormData();
    formData.append('geoObjectId', geoObjectId);
    formData.append('file', file);
    if (description) {
        formData.append('description', description);
    }
    if (tags) {
        formData.append('tags', tags);
    }

    return api.post<Document>('/documents', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  deleteDocument(documentId: string) {
    return api.delete(`/documents/${documentId}`);
  }

  downloadDocument(documentId: string) {
    return api.get(`/documents/${documentId}/download`, {
      responseType: 'blob', // Важно для скачивания файлов
    });
  }

  getOnlyOfficeConfig(documentId: string, mode: 'edit' | 'view' = 'view') {
      // userId и userName должны получаться из состояния аутентификации
      const params = { mode: 'edit'};
      return api.get(`/documents/${documentId}/onlyoffice-config`, { params });
  }
}

export default new DocumentService();