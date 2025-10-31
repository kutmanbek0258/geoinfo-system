package kg.geoinfo.system.apigateway.config.security;

import kg.geoinfo.system.apigateway.config.security.introspector.CustomReactiveTokenIntrospector;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class ResourceServerConfig {

    private final OAuth2ResourceOpaqueProperties resourceProperties;
    private final Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                           ReactiveOpaqueTokenIntrospector introspector) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/auth/**").permitAll()
                .pathMatchers("/v3/api-docs").permitAll()
                .pathMatchers("/api/documents/public/image/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> 
                oauth2.opaqueToken(token -> token.introspector(introspector))
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((exchange, e) -> {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    })
            );
        return http.build();
    }

    @Bean
    public ReactiveOpaqueTokenIntrospector introspector() {
        return new CustomReactiveTokenIntrospector(
            resourceProperties.getIntrospectionUri(),
            resourceProperties.getClientId(),
            resourceProperties.getClientSecret(),
            jackson2ObjectMapperBuilder
        );
    }
}