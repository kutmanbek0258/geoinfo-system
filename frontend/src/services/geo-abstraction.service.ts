import api from "./api";
import type { Page, ImageryLayer } from '@/types/api';
import axios from 'axios';

const API_URL = "/geo-abstraction";

class GeoAbstractionService {
  // --- Direct Upload ---
  async getPresignedUrl(filename: string) {
    const response = await api.get<{ url: string, objectKey: string }>(`${API_URL}/upload/presigned-url`, {
      params: { filename }
    });
    return response.data;
  }

  async uploadFileDirectly(url: string, file: File, onProgress?: (percent: number) => void) {
    return axios.put(url, file, {
      headers: {
        "Content-Type": file.type || "application/octet-stream",
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percentCompleted);
        }
      },
    });
  }

  confirmJob(name: string, objectKey: string, fileSize: number, taskType: string, channels?: string[], indexType?: string, projectId?: string) {
    const params = new URLSearchParams();
    params.append("name", name);
    params.append("objectKey", objectKey);
    params.append("fileSize", fileSize.toString());
    params.append("taskType", taskType);
    
    if (channels) {
      channels.forEach(c => params.append("channels", c));
    }
    if (indexType) {
      params.append("indexType", indexType);
    }
    if (projectId) {
      params.append("projectId", projectId);
    }

    return api.post(`${API_URL}/jobs/confirm`, params, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      }
    });
  }

  // --- Jobs (Multipart methods - preserved for compatibility if needed) ---
  createJob(name: string, file: File, projectId?: string) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    if (projectId) formData.append("projectId", projectId);

    return api.post(`${API_URL}/jobs`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  createSentinelJob(name: string, file: File, channels: string[], indexType?: string, projectId?: string) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    channels.forEach(channel => formData.append("channels", channel));
    if (indexType) {
        formData.append("indexType", indexType);
    }
    if (projectId) formData.append("projectId", projectId);

    return api.post(`${API_URL}/sentinel/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  createLandsatJob(name: string, file: File, channels: string[], indexType?: string, projectId?: string) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    channels.forEach(channel => formData.append("channels", channel));
    if (indexType) {
        formData.append("indexType", indexType);
    }
    if (projectId) formData.append("projectId", projectId);

    return api.post(`${API_URL}/landsat/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  uploadRawGeoTiff(name: string, file: File, projectId?: string) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    if (projectId) formData.append("projectId", projectId);

    return api.post(`${API_URL}/imagery-layer/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  uploadTerrain(name: string, file: File, projectId?: string) {
    const formData = new FormData();
    formData.append("name", name);
    formData.append("file", file);
    if (projectId) formData.append("projectId", projectId);

    return api.post(`${API_URL}/terrain/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  getJob(id: string) {
    return api.get(`${API_URL}/jobs/${id}`);
  }

  getJobs(page: number, size: number, projectId?: string) {
    return api.get(`${API_URL}/jobs`, {
      params: { page, size, projectId }
    });
  }

  // --- Layers ---
  getLayers(page: number, size: number, projectId?: string) {
    return api.get(`${API_URL}/layers`, {
      params: { page, size, projectId }
    });
  }

  deleteLayer(id: string) {
    return api.delete(`${API_URL}/layers/${id}`);
  }

  // --- Imagery Layers ---
  getImageryLayers(page = 0, size = 10, projectId?: string) {
    return api.get<Page<ImageryLayer>>(`${API_URL}/imagery-layer/page-query`, { 
      params: { page, size, projectId } 
    });
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

  getStyles() {
    return api.get<string[]>(`${API_URL}/imagery-layer/styles`);
  }
}

export default new GeoAbstractionService();
