package kg.geoinfo.system.searchservice.controller;

import kg.geoinfo.system.searchservice.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/all")
    public ResponseEntity<Page<Map>> search(
            @RequestParam("query") String query,
            Pageable pageable) {
        Page<Map> results = searchService.search(query, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/geo")
    public ResponseEntity<Page<Map>> searchGeo(
            @RequestParam("query") String query,
            @RequestParam("types") java.util.List<String> types,
            Pageable pageable) {
        Page<Map> results = searchService.searchGeoObjects(query, types, pageable);
        return ResponseEntity.ok(results);
    }
}