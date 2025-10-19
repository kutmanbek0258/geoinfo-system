import api from './api';
import type { Page, SearchResult } from '@/types/api';

class SearchService {
    search(query: string, projectId: string | null, page = 0, size = 20) {
        const types = "point, polygon, multiline";
        return api.get<Page<SearchResult>>('/search/geo', { params: { query, types, page, size, projectId } });
    }
}

export default new SearchService();
