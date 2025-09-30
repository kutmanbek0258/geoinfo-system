
package kg.geoinfo.system.searchservice.service;

import kg.geoinfo.system.common.DocumentEvent;
import kg.geoinfo.system.common.GeoObjectEvent;

public interface IndexingService {

    void indexGeoObject(GeoObjectEvent event);

    void indexDocument(DocumentEvent event);

}
