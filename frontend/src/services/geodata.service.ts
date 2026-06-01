import api from './api';
import type { Page, Project, ImageryLayer, ProjectPoint, ProjectMultiline, ProjectPolygon, GeoFolder, ProjectPointSummary, ProjectMultilineSummary, ProjectPolygonSummary } from '@/types/api';

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

    // --- Import ---
    importFile(file: File, projectName?: string) {
        const formData = new FormData();
        formData.append('file', file);
        if (projectName) {
            formData.append('projectName', projectName);
        }

        return api.post<Project>('/geodata/import/file', formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    }

    importFileToProject(projectId: string, file: File) {
        const formData = new FormData();
        formData.append('file', file);

        return api.post<Project>(`/geodata/import/file/${projectId}`, formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    }

    // --- Points ---
    getPointsByProjectId(projectId: string, page = 0, size = 1000) {
        return api.get<Page<ProjectPoint>>(`/geodata/points/by-project-id/${projectId}`, { params: { page, size } });
    }
    getPointsSummaryByProjectId(projectId: string, page = 0, size = 1000) {
        return api.get<Page<ProjectPointSummary>>(`/geodata/points/by-project-id/${projectId}/summary`, { params: { page, size } });
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
    getMultilinesByProjectId(projectId: string, page = 0, size = 1000) {
        return api.get<Page<ProjectMultiline>>(`/geodata/multilines/by-project-id/${projectId}`, { params: { page, size } });
    }
    getMultilinesSummaryByProjectId(projectId: string, page = 0, size = 1000) {
        return api.get<Page<ProjectMultilineSummary>>(`/geodata/multilines/by-project-id/${projectId}/summary`, { params: { page, size } });
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
    getPolygonsByProjectId(projectId: string, page = 0, size = 1000) {
        return api.get<Page<ProjectPolygon>>(`/geodata/polygons/by-project-id/${projectId}`, { params: { page, size } });
    }
    getPolygonsSummaryByProjectId(projectId: string, page = 0, size = 1000) {
        return api.get<Page<ProjectPolygonSummary>>(`/geodata/polygons/by-project-id/${projectId}/summary`, { params: { page, size } });
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

    // --- Folders ---
    getFoldersByProjectId(projectId: string) {
        return api.get<GeoFolder[]>(`/geodata/folders/project/${projectId}`);
    }
    createFolder(folder: Omit<GeoFolder, 'id'>) {
        return api.post<GeoFolder>('/geodata/folders', folder);
    }
    updateFolder(id: string, folder: Partial<Omit<GeoFolder, 'id'>>) {
        return api.put<GeoFolder>(`/geodata/folders/${id}`, folder);
    }
    deleteFolder(id: string) {
        return api.delete(`/geodata/folders/${id}`);
    }
}

export default new GeodataService();
