import api from "./api";

const API_URL = "/terrain";

class TerrainService {
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

  getJob(id: string) {
    return api.get(`${API_URL}/jobs/${id}`);
  }

  getLayers(page: number, size: number) {
    return api.get(`${API_URL}/layers`, {
      params: { page, size }
    });
  }

  deleteLayer(id: string) {
    return api.delete(`${API_URL}/layers/${id}`);
  }
}

export default new TerrainService();
