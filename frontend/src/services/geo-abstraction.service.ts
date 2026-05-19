import api from "./api";
import type { Page, ImageryLayer } from '@/types/api';

const API_URL = "/geo-abstraction";

class GeoAbstractionService {
  // --- Jobs ---
  createJob(projectId: string, name: string, file: File) {
    const formData = new FormData();
    formData.append("projectId", projectId);
    formData.append("name", name);
    formData.append("file", file);

    return api.post(`${API_URL}/jobs`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  createSentinelJob(name: string, file: File, channels: string[], indexType?: string) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    channels.forEach(channel => formData.append("channels", channel));
    if (indexType) {
        formData.append("indexType", indexType);
    }

    return api.post(`${API_URL}/sentinel/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  createLandsatJob(name: string, file: File, channels: string[], indexType?: string) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    channels.forEach(channel => formData.append("channels", channel));
    if (indexType) {
        formData.append("indexType", indexType);
    }

    return api.post(`${API_URL}/landsat/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  uploadRawGeoTiff(name: string, file: File) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);

    return api.post(`${API_URL}/imagery-layer/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  uploadTerrain(name: string, file: File) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);

    return api.post(`${API_URL}/terrain/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  getJob(id: string) {
    return api.get(`${API_URL}/jobs/${id}`);
  }

  getJobs(page: number, size: number) {
    return api.get(`${API_URL}/jobs`, {
      params: { page, size }
    });
  }

  // --- Layers ---
  getLayers(page: number, size: number) {
    return api.get(`${API_URL}/layers`, {
      params: { page, size }
    });
  }

  deleteLayer(id: string) {
    return api.delete(`${API_URL}/layers/${id}`);
  }

  // --- Imagery Layers ---
  getImageryLayers(page = 0, size = 10) {
    return api.get<Page<ImageryLayer>>(`${API_URL}/imagery-layer/page-query`, { params: { page, size } });
  }

  getImageryLayerById(id: string) {
    return api.get<ImageryLayer>(`${API_URL}/imagery-layer/${id}`);
  }

  createImageryLayer(layer: Omit<ImageryLayer, 'id'>) {
    return api.post<ImageryLayer>(`${API_URL}/imagery-layer`, layer);
  }

  updateImageryLayer(id: string, layer: Partial<Omit<ImageryLayer, 'id'>>) {
    return api.put<ImageryLayer>(`${API_URL}/imagery-layer/${id}`, layer);
  }

  deleteImageryLayer(id: string) {
    return api.delete(`${API_URL}/imagery-layer/${id}`);
  }
}

export default new GeoAbstractionService();
