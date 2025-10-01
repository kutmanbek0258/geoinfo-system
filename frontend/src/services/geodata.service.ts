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

    // --- Imagery Layers ---
    getImageryLayers(page = 0, size = 10) {
        return api.get<Page<ImageryLayer>>('/geodata/imagery-layer/page-query', { params: { page, size } });
    }

    // --- Points ---
    getPointsByProjectId(projectId: string, page = 0, size = 10) {
        return api.get<Page<ProjectPoint>>(`/geodata/points/by-project-id/${projectId}`, { params: { page, size } });
    }
    createPoint(point: Omit<ProjectPoint, 'id'>) {
        return api.post<ProjectPoint>('/geodata/points', point);
    }
    // ... другие методы для CRUD Points

    // --- Multilines ---
    getMultilinesByProjectId(projectId: string, page = 0, size = 10) {
        return api.get<Page<ProjectMultiline>>(`/geodata/multilines/by-project-id/${projectId}`, { params: { page, size } });
    }
    // ... другие методы для CRUD Multilines

    // --- Polygons ---
    getPolygonsByProjectId(projectId: string, page = 0, size = 10) {
        return api.get<Page<ProjectPolygon>>(`/geodata/polygons/by-project-id/${projectId}`, { params: { page, size } });
    }
    // ... другие методы для CRUD Polygons
}

export default new GeodataService();
