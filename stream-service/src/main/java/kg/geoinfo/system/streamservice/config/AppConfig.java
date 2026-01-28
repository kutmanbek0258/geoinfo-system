package kg.geoinfo.system.streamservice.config;

import kg.geoinfo.system.streamservice.config.security.OAuth2ResourceOpaqueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final OAuth2ResourceOpaqueProperties introspectionProperties;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .basicAuthentication(introspectionProperties.getClientId(), introspectionProperties.getClientSecret())
                .build();
    }
}
