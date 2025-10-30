import { createStore } from 'vuex';
import { alert } from './alert.module';
import geodata from './geodata.store';
import document from './document.store';
import search from './search.store';

export interface RootState {}

export const store = createStore<RootState>({
    modules: {
        alert,
        geodata,
        document,
        search
    },
});