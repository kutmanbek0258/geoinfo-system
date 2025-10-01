import { createStore } from 'vuex';
import { alert, type AlertState } from './alert.module';
import geodata from './geodata.store';
import document from './document.store';
import search from './search.store';

// Определяем корневое состояние, которое будет включать состояния всех модулей
export interface RootState {
    alert: AlertState;
    // Добавьте сюда интерфейсы состояний для других модулей, если необходимо
    // geodata: GeodataState;
    // document: DocumentState;
    // search: SearchState;
}

export default createStore<RootState>({
    modules: {
        alert,
        geodata,
        document,
        search,
    },
});
