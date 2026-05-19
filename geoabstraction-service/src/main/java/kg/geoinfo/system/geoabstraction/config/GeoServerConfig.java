package kg.geoinfo.system.geoabstraction.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeoServerConfig {

    @Bean
    public RestTemplate geoServerRestTemplate(GeoServerProperties properties, RestTemplateBuilder builder) {
        return builder
                .basicAuthentication(properties.getUsername(), properties.getPassword())
                .build();
    }
}
