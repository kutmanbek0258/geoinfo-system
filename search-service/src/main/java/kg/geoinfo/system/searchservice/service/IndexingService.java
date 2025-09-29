
package kg.geoinfo.system.searchservice.service;

import kg.geoinfo.system.searchservice.dto.DocumentEvent;
import kg.geoinfo.system.searchservice.dto.GeoObjectEvent;

public interface IndexingService {

    void indexGeoObject(GeoObjectEvent event);

    void indexDocument(DocumentEvent event);

}
