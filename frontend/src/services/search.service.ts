import api from './api';
import type { Page, SearchResult } from '@/types/api';

class SearchService {
    search(query: string, page = 0, size = 20) {
        return api.get<Page<SearchResult>>('/search', { params: { query, page, size } });
    }
}

export default new SearchService();
