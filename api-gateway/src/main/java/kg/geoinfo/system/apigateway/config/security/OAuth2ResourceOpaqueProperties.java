package kg.geoinfo.system.apigateway.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.opaquetoken")
public class OAuth2ResourceOpaqueProperties {
    private String introspectionUri;
    private String clientId;
    private String clientSecret;
}