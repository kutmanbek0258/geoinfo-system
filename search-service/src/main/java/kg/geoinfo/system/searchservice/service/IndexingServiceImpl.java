
package kg.geoinfo.system.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.searchservice.domain.DocumentIndex;
import kg.geoinfo.system.common.DocumentEvent;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.searchservice.domain.GeoObjectIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private static final String GEO_INDEX_NAME = "geo_index";
    private static final String DOC_INDEX_NAME = "document_index";

    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;

    @Override
    public void indexGeoObject(GeoObjectEvent event) {
        String id = event.getPayload().get("id").toString();
        if (event.getEventType() == GeoObjectEvent.EventType.DELETED) {
            deleteDocumentFromIndex(GEO_INDEX_NAME, id);
        } else { // CREATED or UPDATED
            GeoObjectIndex geoObjectIndex = objectMapper.convertValue(event.getPayload(), GeoObjectIndex.class);
            indexDocument(GEO_INDEX_NAME, id, geoObjectIndex);
        }
    }

    @Override
    public void indexDocument(DocumentEvent event) {
        String id = event.getPayload().get("id").toString();
        if (event.getEventType() == DocumentEvent.EventType.DELETED) {
            deleteDocumentFromIndex(DOC_INDEX_NAME, id);
        } else { // CREATED or UPDATED
            DocumentIndex documentIndex = objectMapper.convertValue(event.getPayload(), DocumentIndex.class);
            indexDocument(DOC_INDEX_NAME, id, documentIndex);
        }
    }

    private <T> void indexDocument(String indexName, String id, T document) {
        try {
            elasticsearchClient.index(i -> i
                    .index(indexName)
                    .id(id)
                    .document(document)
            );
            log.info("Indexed document {} in index {}", id, indexName);
        } catch (IOException e) {
            log.error("Error indexing document {} in index {}: {}", id, indexName, e.getMessage());
            // TODO: Add to dead-letter queue or other error handling
        }
    }

    private void deleteDocumentFromIndex(String indexName, String id) {
        try {
            elasticsearchClient.delete(d -> d
                    .index(indexName)
                    .id(id)
            );
            log.info("Deleted document {} from index {}", id, indexName);
        } catch (IOException e) {
            log.error("Error deleting document {} from index {}: {}", id, indexName, e.getMessage());
            // TODO: Add to dead-letter queue or other error handling
        }
    }
}
