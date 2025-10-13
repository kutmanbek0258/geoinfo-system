import api from './api';
import type { Page, Project, ImageryLayer, ProjectPoint, ProjectMultiline, ProjectPolygon } from '@/types/api';

class GeodataService {
    // --- Projects ---
    getProjects(page = 0, size = 10) {
        return api.get<Page<Project>>('/geodata/project/page-query', { params: { page, size } });
    }
    createProject(project: Omit<Project, 'id'>) {
        return api.post<Project>('/geodata/project', project);
    }
    updateProject(id: string, project: Partial<Omit<Project, 'id'>>) {
        return api.put<Project>(`/geodata/project/${id}`, project);
    }
    deleteProject(id: string) {
        return api.delete(`/geodata/project/${id}`);
    }
    getProjectById(id: string) {
        return api.get<Project>(`/geodata/project/${id}`);
    }

    shareProject(projectId: string, email: string, permissionLevel: string) {
        return api.post(`/geodata/project/${projectId}/share`, { email, permissionLevel });
    }

    // --- Imagery Layers ---
    getImageryLayers(page = 0, size = 10) {
        return api.get<Page<ImageryLayer>>('/geodata/imagery-layer/page-query', { params: { page, size } });
    }
    createImageryLayer(layer: Omit<ImageryLayer, 'id'>) {
        return api.post<void>('/geodata/imagery-layer', layer);
    }
    updateImageryLayer(id: string, layer: Partial<Omit<ImageryLayer, 'id'>>) {
        return api.put<void>(`/geodata/imagery-layer/${id}`, layer);
    }
    deleteImageryLayer(id: string) {
        return api.delete(`/geodata/imagery-layer/${id}`);
    }
    getImageryLayerById(id: string) {
        return api.get<ImageryLayer>(`/geodata/imagery-layer/${id}`);
    }

    // --- Points ---
    getPointsByProjectId(projectId: string, page = 0, size = 10) {
        return api.get<Page<ProjectPoint>>(`/geodata/points/by-project-id/${projectId}`, { params: { page, size } });
    }
    createPoint(point: Omit<ProjectPoint, 'id'>) {
        return api.post<ProjectPoint>('/geodata/points', point);
    }
    updatePoint(id: string, point: Partial<Omit<ProjectPoint, 'id'>>) {
        return api.put<ProjectPoint>(`/geodata/points/${id}`, point);
    }
    deletePoint(id: string) {
        return api.delete(`/geodata/points/${id}`);
    }
    getPointById(id: string) {
        return api.get<ProjectPoint>(`/geodata/points/${id}`);
    }

    // --- Multilines ---
    getMultilinesByProjectId(projectId: string, page = 0, size = 10) {
        return api.get<Page<ProjectMultiline>>(`/geodata/multilines/by-project-id/${projectId}`, { params: { page, size } });
    }
    createMultiline(multiline: Omit<ProjectMultiline, 'id'>) {
        return api.post<ProjectMultiline>('/geodata/multilines', multiline);
    }
    updateMultiline(id: string, multiline: Partial<Omit<ProjectMultiline, 'id'>>) {
        return api.put<ProjectMultiline>(`/geodata/multilines/${id}`, multiline);
    }
    deleteMultiline(id: string) {
        return api.delete(`/geodata/multilines/${id}`);
    }
    getMultilineById(id: string) {
        return api.get<ProjectMultiline>(`/geodata/multilines/${id}`);
    }

    // --- Polygons ---
    getPolygonsByProjectId(projectId: string, page = 0, size = 10) {
        return api.get<Page<ProjectPolygon>>(`/geodata/polygons/by-project-id/${projectId}`, { params: { page, size } });
    }
    createPolygon(polygon: Omit<ProjectPolygon, 'id'>) {
        return api.post<ProjectPolygon>('/geodata/polygons', polygon);
    }
    updatePolygon(id: string, polygon: Partial<Omit<ProjectPolygon, 'id'>>) {
        return api.put<ProjectPolygon>(`/geodata/polygons/${id}`, polygon);
    }
    deletePolygon(id: string) {
        return api.delete(`/geodata/polygons/${id}`);
    }
    getPolygonById(id: string) {
        return api.get<ProjectPolygon>(`/geodata/polygons/${id}`);
    }

    uploadMainImage(objectType: 'points' | 'multilines' | 'polygons', objectId: string, file: File) {
        const formData = new FormData();
        formData.append('file', file);

        return api.post(`/geodata/${objectType}/${objectId}/upload-main-image`, formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    }
}

export default new GeodataService();
