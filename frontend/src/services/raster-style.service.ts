import axios from "axios";
import api from "./api";
import type { Page, RasterStyle } from '@/types/api';

const API_URL = "/geo-abstraction/raster-style";

class RasterStyleService {
  async getTiTilerColorMaps(): Promise<string[]> {
    const response = await axios.get<any>('/raster/cog/colorMaps?f=json');
    if (response.data && response.data.colormaps && Array.isArray(response.data.colormaps)) {
      return response.data.colormaps.map((c: any) => c.id).sort((a: string, b: string) => a.localeCompare(b));
    }
    return [];
  }

  async getRasterValueAtPoint(s3Url: string, lon: number, lat: number): Promise<any> {
    const url = `/raster/cog/cog/point/${lon},${lat}?url=${encodeURIComponent(s3Url)}`;
    const response = await axios.get<any>(url);
    return response.data;
  }

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
