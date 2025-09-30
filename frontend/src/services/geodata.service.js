import api from "./api";

const API_URL = "/api"; // Предполагаемый базовый URL для geodata-service в API Gateway

class GeodataService {
    // --- Projects ---

    getProjects() {
        return api.get(`${API_URL}/projects/page-query`);
    }

    getProjectById(id) {
        return api.get(`${API_URL}/projects/${id}`);
    }

    createProject(projectData) {
        return api.post(`${API_URL}/projects`, projectData);
    }

    updateProject(id, projectData) {
        return api.put(`${API_URL}/projects/${id}`, projectData);
    }

    deleteProject(id) {
        return api.delete(`${API_URL}/projects/${id}`);
    }

    // --- Geo Objects (Points, Lines, Polygons) ---

    getFeaturesForProject(projectId, featureType) {
        // featureType может быть 'points', 'lines', 'polygons'
        return api.get(`${API_URL}/projects/${projectId}/${featureType}`);
    }

    createFeature(projectId, featureType, featureData) {
        return api.post(`${API_URL}/projects/${projectId}/${featureType}`, featureData);
    }

    updateFeature(featureType, featureId, featureData) {
        return api.put(`${API_URL}/${featureType}/${featureId}`, featureData);
    }

    deleteFeature(featureType, featureId) {
        return api.delete(`${API_URL}/${featureType}/${featureId}`);
    }

    // --- KML Import ---

    importKml(projectId, file) {
        const formData = new FormData();
        formData.append("file", file);

        return api.post(`${API_URL}/projects/${projectId}/kml-import`, formData, {
            headers: {
                "Content-Type": "multipart/form-data",
            },
        });
    }

    // --- Imagery Layers ---

    getImageryLayers() {
        return api.get(`${API_URL}/imagery-layers`);
    }
}

export default new GeodataService();
