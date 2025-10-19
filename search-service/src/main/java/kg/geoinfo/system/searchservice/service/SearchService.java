package kg.geoinfo.system.searchservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface SearchService {

    Page<Map> search(String query, Pageable pageable);

    Page<Map> searchGeoObjects(String query, java.util.List<String> types, Pageable pageable, String projectId);

}