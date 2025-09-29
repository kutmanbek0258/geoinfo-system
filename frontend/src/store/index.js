import { createStore } from "vuex";

import { alertModule } from './alert.module';
import { companyModule } from "./company.module";
import { documentModule } from "./document.module";
import { geodataModule } from "./geodata.module";

export const store = createStore({
    modules: {
        alert: alertModule,
        company: companyModule,
        document: documentModule,
        geodata: geodataModule,
    }
});
