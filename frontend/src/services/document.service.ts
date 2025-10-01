import api from './api';
import type { Document } from '@/types/api';

class DocumentService {
    getDocumentsForGeoObject(geoObjectId: string) {
        return api.get<Document[]>(`/documents/geo/${geoObjectId}`);
    }

    uploadDocument(file: File, geoObjectId: string, description: string, tags: string) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('geoObjectId', geoObjectId);
        formData.append('description', description);
        formData.append('tags', tags);

        return api.post<Document>('/documents', formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    }

    deleteDocument(id: string) {
        return api.delete(`/documents/${id}`);
    }

    updateDocument(id: string, data: { description?: string; tags?: string[] }) {
        return api.put<Document>(`/documents/${id}`, data);
    }
    
    getDocumentDownloadUrl(id: string): string {
        return `${api.defaults.baseURL}/documents/${id}/download`;
    }
}

export default new DocumentService();
