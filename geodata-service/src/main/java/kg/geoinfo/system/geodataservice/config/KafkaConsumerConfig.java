package kg.geoinfo.system.geodataservice.config;

import kg.geoinfo.system.common.GeoAnalysisResultEvent;
import kg.geoinfo.system.common.GeoVectorExportRequest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:geodata-service-group}")
    private String groupId;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> analysisResultsListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        JsonDeserializer<GeoAnalysisResultEvent> deserializer = new JsonDeserializer<>(GeoAnalysisResultEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        
        @SuppressWarnings("unchecked")
        Deserializer<Object> objectDeserializer = (Deserializer<Object>) (Deserializer<?>) deserializer;
        
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ErrorHandlingDeserializer<>(objectDeserializer)));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> vectorExportListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        JsonDeserializer<GeoVectorExportRequest> deserializer = new JsonDeserializer<>(GeoVectorExportRequest.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        
        @SuppressWarnings("unchecked")
        Deserializer<Object> objectDeserializer = (Deserializer<Object>) (Deserializer<?>) deserializer;
        
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ErrorHandlingDeserializer<>(objectDeserializer)));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> processedLayersListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        @SuppressWarnings("rawtypes")
        JsonDeserializer<Map> deserializer = new JsonDeserializer<>(Map.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        
        @SuppressWarnings("unchecked")
        Deserializer<Object> objectDeserializer = (Deserializer<Object>) (Deserializer<?>) deserializer;
        
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ErrorHandlingDeserializer<>(objectDeserializer)));
        return factory;
    }
}
