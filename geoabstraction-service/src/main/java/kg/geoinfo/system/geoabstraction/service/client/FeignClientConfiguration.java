package kg.geoinfo.system.geoabstraction.service.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;

/**
 * Конфигурация Feign-клиентов: пробрасывает токен OAuth2 из SecurityContext
 * в каждый исходящий межсервисный запрос.
 */
@Configuration
public class FeignClientConfiguration {

    @Bean
    public RequestInterceptor oauth2FeignRequestInterceptor() {
        return requestTemplate -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() instanceof AbstractOAuth2Token token) {
                requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue());
            }
        };
    }
}
