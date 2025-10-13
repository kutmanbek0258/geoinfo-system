package kg.geoinfo.system.geodataservice.service.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;

@Configuration
public class FeignClientConfiguration {

    @Bean
    public RequestInterceptor oauth2FeignRequestInterceptor() {
        return requestTemplate -> {
            // предотвращаем рекурсию: не добавляем токен при обращении к auth-service
            if (requestTemplate.url().contains("auth-service") ||
                    requestTemplate.url().contains("/oauth2/token") ||
                    requestTemplate.url().contains("/oauth2/token-info")) {
                return;
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() instanceof AbstractOAuth2Token token) {
                requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue());
            }
        };
    }
}
