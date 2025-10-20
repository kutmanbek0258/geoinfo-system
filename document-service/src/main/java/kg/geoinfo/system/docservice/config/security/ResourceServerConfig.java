package kg.geoinfo.system.docservice.config.security;

import kg.geoinfo.system.docservice.config.security.introspector.CustomSpringTokenIntrospection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity(debug = true)
@RequiredArgsConstructor
public class ResourceServerConfig {

    private final OAuth2ResourceOpaqueProperties resourceProperties;
    private final MappingJackson2HttpMessageConverter messageConverter;

    @Bean
    @Order(1) // Приоритет для публичной цепочки
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/v3/api-docs/**", "/api/documents/public/image/**", "/api/documents/*/content")
                .authorizeHttpRequests(customizer -> customizer.anyRequest().permitAll())
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    @Order(2) // Основная цепочка безопасности
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // выключаем поддержку сессий
        http.sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(customizer -> {
                    customizer.anyRequest().authenticated();
                });

        // подключаем поддержку OAuth2 Resource Server с Opaque Token.
        // добавляем в качестве introspector нашу реализацию
        http.oauth2ResourceServer(configurer -> {
            configurer.opaqueToken(customizer -> {
                customizer.introspector(new CustomSpringTokenIntrospection(
                        resourceProperties.getIntrospectionUri(),
                        resourceProperties.getClientId(),
                        resourceProperties.getClientSecret(),
                        messageConverter
                ));
            });
        });
        return http.build();
    }
}
