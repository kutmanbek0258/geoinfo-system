package kg.geoinfo.system.geoabstraction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "geoserver")
@Getter
@Setter
public class GeoServerProperties {
    private String url;
    private String domain;
    private String username;
    private String password;
    private String workspace;
}
