import api from './api';
import type { SearchResult } from '@/types/api';

class SearchService {
    search(query: string) {
        return api.get<SearchResult[]>('/search', { params: { query } });
    }
}

export default new SearchService();
