import api from './api';
import type { Page, SearchResult } from '@/types/api';

class SearchService {
    search(query: string, page = 0, size = 20) {
        const types = "point, polygon, multiline";
        return api.get<Page<SearchResult>>('/search/geo', { params: { query, types, page, size } });
    }
}

export default new SearchService();
