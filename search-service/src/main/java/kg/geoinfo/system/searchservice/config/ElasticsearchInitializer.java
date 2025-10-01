package kg.geoinfo.system.searchservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ElasticsearchInitializer {

    private final ElasticsearchClient client;

    // список индексов, которые должны существовать
    private static final List<String> INDEXES = List.of("geo_index", "document_index");

    @PostConstruct
    public void initIndexes() throws IOException {
        for (String index : INDEXES) {
            boolean exists = client.indices()
                    .exists(ExistsRequest.of(e -> e.index(index)))
                    .value();

            if (!exists) {
                client.indices().create(CreateIndexRequest.of(c -> c.index(index)));
                System.out.println("✅ Created index: " + index);
            } else {
                System.out.println("ℹ️ Index already exists: " + index);
            }
        }
    }
}