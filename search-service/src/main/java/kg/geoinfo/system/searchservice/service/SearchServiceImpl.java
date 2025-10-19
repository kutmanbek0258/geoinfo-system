
package kg.geoinfo.system.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public Page<Map> search(String query, Pageable pageable) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index("geo_index", "document_index") // Search across both indices
                    .query(q -> q
                            .multiMatch(m -> m
                                    .query(query)
                                    .fields("name", "description", "file_name", "content", "tags")
                            )
                    )
                    .from((int) pageable.getOffset())
                    .size(pageable.getPageSize()),
                    Map.class
            );

            List<Map> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total().value();

            return new PageImpl<>(results, pageable, totalHits);

        } catch (IOException e) {
            log.error("Error during Elasticsearch search: {}", e.getMessage());
            // TODO: Better exception handling
            throw new RuntimeException("Error performing search", e);
        }
    }

    @Override
    public Page<Map> searchGeoObjects(String query, List<String> types, Pageable pageable, String projectId) {
        if (types == null || types.isEmpty()) {
            return Page.empty(pageable);
        }

        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> {
                s.index("geo_index") // Search only in geo_index
                        .query(q -> q
                                .bool(b -> {
                                    b.must(m -> m
                                            .multiMatch(mm -> mm
                                                    .query(query)
                                                    .fields("name", "description")
                                            )
                                    );
                                    b.filter(f -> f
                                            .terms(t -> t
                                                    .field("type")
                                                    .terms(ts -> ts.value(types.stream().map(FieldValue::of).collect(Collectors.toList())))
                                            )
                                    );
                                    if (projectId != null && !projectId.isEmpty()) {
                                        b.filter(f -> f
                                                .match(m -> m
                                                        .field("projectId")
                                                        .query(projectId)
                                                )
                                        );
                                    }
                                    return b;
                                }
                                )
                        )
                        .from((int) pageable.getOffset())
                        .size(pageable.getPageSize());
                return s;
            },
                    Map.class
            );

            List<Map> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total().value();

            return new PageImpl<>(results, pageable, totalHits);

        } catch (IOException e) {
            log.error("Error during Elasticsearch geo search: {}", e.getMessage());
            throw new RuntimeException("Error performing geo search", e);
        }
    }
}
