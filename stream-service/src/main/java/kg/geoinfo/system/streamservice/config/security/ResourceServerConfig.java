package kg.geoinfo.system.streamservice.config.security;

import kg.geoinfo.system.streamservice.config.security.introspector.CustomSpringTokenIntrospection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity(debug = true)
@RequiredArgsConstructor
public class ResourceServerConfig {

    private final OAuth2ResourceOpaqueProperties resourceProperties;
    private final MappingJackson2HttpMessageConverter messageConverter;

    @Bean
    @Order(1)
    public SecurityFilterChain publicEndpointsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/streams/auth", "/v3/api-docs/**", "/swagger-ui/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()) // Добавьте, если MediaMTX и фронтенд на разных доменах
                .authorizeHttpRequests(authorize -> authorize
                        // Явно разрешаем POST для MediaMTX
                        .requestMatchers(HttpMethod.POST, "/api/streams/auth").permitAll()
                        .anyRequest().permitAll()
                )
                // Отключаем сессии для этого эндпоинта
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(configurer -> configurer.opaqueToken(customizer -> {
                    customizer.introspector(new CustomSpringTokenIntrospection(
                            resourceProperties.getIntrospectionUri(),
                            resourceProperties.getClientId(),
                            resourceProperties.getClientSecret(),
                            messageConverter
                    ));
                }))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
