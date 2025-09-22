package kg.geoinfo.system.apigateway.config.security.introspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.apigateway.dto.TokenInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class CustomReactiveTokenIntrospector implements ReactiveOpaqueTokenIntrospector {

    private final WebClient webClient;
    private final String introspectionUri;

    private final ObjectMapper objectMapper;

    public CustomReactiveTokenIntrospector(
            String introspectionUri,
            String clientId,
            String clientSecret,
            org.springframework.http.converter.json.Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder) {
        this.introspectionUri = introspectionUri;
        this.objectMapper = jackson2ObjectMapperBuilder.build();
        this.webClient = WebClient.builder()
                .baseUrl(introspectionUri)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + java.util.Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    @Override
    public Mono<OAuth2AuthenticatedPrincipal> introspect(String token) {
        return webClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("token=" + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    // Подробное логирование
                                    log.error("Introspection endpoint error: status={}, body={}", clientResponse.statusCode(), errorBody);
                                    return Mono.error(new OAuth2IntrospectionException(
                                            "Introspection endpoint error: " + clientResponse.statusCode() + " " + errorBody
                                    ));
                                })
                )
                .bodyToMono(TokenInfoDto.class)
                .flatMap(tokenInfo -> {
                    if (tokenInfo == null || !Boolean.TRUE.equals(tokenInfo.getActive())) {
                        return Mono.error(new BadOpaqueTokenException("Inactive token"));
                    }
                    return Mono.just((OAuth2AuthenticatedPrincipal) new IntrospectionPrincipal(tokenInfo));
                })
                .onErrorMap(e -> {
                    log.error("Token introspection failed", e);
                    return new OAuth2IntrospectionException("Token introspection failed: " + e.getMessage(), e);
                });
    }
}