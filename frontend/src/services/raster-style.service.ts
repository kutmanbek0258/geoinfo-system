import api from "./api";
import type { Page, RasterStyle } from '@/types/api';

const API_URL = "/geo-abstraction/raster-style";

class RasterStyleService {
  getRasterStyles(page = 0, size = 10, name?: string, title?: string) {
    return api.get<Page<RasterStyle>>(API_URL + "/page-query", {
      params: { page, size, name, title }
    });
  }

  getRasterStyleById(id: string) {
    return api.get<RasterStyle>(`${API_URL}/${id}`);
  }

  createRasterStyle(style: Omit<RasterStyle, 'id'>) {
    return api.post<RasterStyle>(API_URL, style);
  }

  updateRasterStyle(id: string, style: Partial<Omit<RasterStyle, 'id'>>) {
    return api.put<RasterStyle>(`${API_URL}/${id}`, style);
  }

  deleteRasterStyle(id: string) {
    return api.delete(`${API_URL}/${id}`);
  }
}

export default new RasterStyleService();
