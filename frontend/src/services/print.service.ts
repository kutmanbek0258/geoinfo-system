import api from './api';

const API_URL = "/print";

export interface PrintSpecification {
  projectId: string;
  layout: string;
  dpi: number;
  mapContext: {
    projection: string;
    bbox: number[];
    rotation: number;
  };
  layers: any[];
  attributes: {
    title: string;
    author: string;
    organization: string;
  };
}

export interface PrintTask {
  id: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  layout: string;
  s3Url: string;
  errorMessage: string;
  attributes: any;
  createdDate: string;
}

class PrintService {
  async createPrintTask(spec: PrintSpecification): Promise<PrintTask> {
    const response = await api.post(`${API_URL}/tasks`, spec);
    return response.data;
  }

  async getPrintTask(id: string): Promise<PrintTask> {
    const response = await api.get(`${API_URL}/tasks/${id}`);
    return response.data;
  }
}

export default new PrintService();
