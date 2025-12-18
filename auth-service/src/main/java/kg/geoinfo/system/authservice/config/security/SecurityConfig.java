package kg.geoinfo.system.authservice.config.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import jakarta.annotation.PostConstruct;
import kg.geoinfo.system.authservice.config.SecurityProperties;
import kg.geoinfo.system.authservice.config.filter.XSSFilter;
import kg.geoinfo.system.authservice.config.security.handler.CustomAuthenticationSuccessHandler;
import kg.geoinfo.system.authservice.config.security.handler.CustomOauthAuthenticationSuccessHandler;
import kg.geoinfo.system.authservice.config.security.properties.AuthorizationServerProperties;
import kg.geoinfo.system.authservice.service.UserEventService;
import kg.geoinfo.system.authservice.service.impl.CustomOAuth2UserService;
import kg.geoinfo.system.authservice.service.impl.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.owasp.encoder.Encode;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Конфигурация SecurityFilterChain web приложения SSO.
 * WEB приложение SSO включает в себя управление данными пользователя и админка.
 */
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String LOGIN_PAGE = "/client/auth/login";
    public static final String LOGIN_PROCESSING_URL = "/login";
    public static final String LOGOUT_PROCESSING_URL = "/logout";
    public static final String[] PERMIT_ALL_PATTERNS = {
            LOGIN_PAGE,
            "/error**",
            "/static/**",
            "/client/**",
            "/registration/**",
            "/reset-password/**",
            "/",
            "/v3/api-docs"
    };

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomUserDetailsService userDetailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationServerProperties authorizationServerProperties;
    private final UserEventService eventService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityProperties.Headers securityHeaderProperties;

    // handlers
    private AuthenticationSuccessHandler oAuth2successHandler;
    private AuthenticationSuccessHandler loginRequestSuccessHandler;
    private AuthenticationFailureHandler failureHandler;

    @Order(1)
    @Bean
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {

        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http
                .cors(Customizer.withDefaults())   // 🔥 ВАЖНО
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Order(2)
    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        SocialConfigurer socialConfigurer = new SocialConfigurer()
                .oAuth2UserService(customOAuth2UserService)
                .successHandler(oAuth2successHandler)
                .failureHandler(failureHandler)
                .formLogin(LOGIN_PAGE);

        http.with(socialConfigurer, Customizer.withDefaults());
        http.csrf(configurer -> configurer
                .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                .csrfTokenRequestHandler(new CustomCsrfTokenRequestHandler())
        );

        http.cors(Customizer.withDefaults()); // Enable CORS

        http.headers(customizer -> {
            customizer.contentSecurityPolicy(
                    configurer -> configurer.policyDirectives(securityHeaderProperties.getCSPLikeString())
            );

            // Настройка заголовка X-Frame-Options
            customizer.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);

            // Настройка заголовка Strict-Transport-Security
            customizer.httpStrictTransportSecurity(configurer -> configurer
                    // указываем какое количество времени использовать HTTPS сразу
                    .maxAgeInSeconds(securityHeaderProperties.getHsts().getMaxAge())
                    // применять ли те же правила для поддоменов
                    .includeSubDomains(securityHeaderProperties.getHsts().getIncludeSubDomains())
                    // использовать ли предварительную загрузку (https://hstspreload.org/)
                    .preload(securityHeaderProperties.getHsts().getPreload())
            );

            // Настройка заголовка Permissions-Policy
            customizer.permissionsPolicy(configurer -> configurer.policy(
                    securityHeaderProperties.getPermissionPolicyLikeString()
            ));
        });

        http.securityContext(customizer -> customizer.securityContextRepository(securityContextRepository));

        http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailService)
                .passwordEncoder(passwordEncoder);

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
                .anyRequest().authenticated()
        );

        http.exceptionHandling(configurer -> {
            configurer.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        });
        http.logout(configurer -> {
            configurer
                    .logoutUrl(LOGOUT_PROCESSING_URL)
                    .clearAuthentication(true)
                    .logoutSuccessHandler((request, response, authentication) -> {
                        response.setStatus(HttpStatus.OK.value());
                    });
        });
        return http.formLogin(configurer -> {
            configurer.loginPage(LOGIN_PAGE)
                    .loginProcessingUrl(LOGIN_PROCESSING_URL)
                    .successHandler(loginRequestSuccessHandler)
                    .failureHandler(failureHandler);
        }).build();
    }

    @Bean
    public SecurityContextLogoutHandler securityContextLogoutHandler() {
        return new SecurityContextLogoutHandler();
    }

    @Bean
    public StrictHttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(Arrays.asList("GET", "POST", "DELETE", "OPTIONS"));
        return firewall;
    }

    @PostConstruct
    private void initializeHandlers() {
        this.loginRequestSuccessHandler = new CustomAuthenticationSuccessHandler(
                authorizationServerProperties.getAuthenticationSuccessUrl(),
                authorizationServerProperties.getCustomHandlerHeaderName(),
                authorizationServerProperties.getSavedRequestUrlStartsWith(),
                eventService
        );

        this.oAuth2successHandler = new CustomOauthAuthenticationSuccessHandler(
                authorizationServerProperties.getAuthenticationSuccessUrl(),
                eventService
        );

        this.failureHandler = new SimpleUrlAuthenticationFailureHandler();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer objectMapperBuilder() {
        return builder -> builder.deserializerByType(String.class, new JsonDeserializer<String>() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException {
                String origin = p.getValueAsString();
                return Encode.forHtmlContent(origin);
            }
        });
    }

    @Bean
    public FilterRegistrationBean<XSSFilter> xssFilter() {
        XSSFilter xssFilter = new XSSFilter();
        FilterRegistrationBean<XSSFilter> registrationBean = new FilterRegistrationBean<>(xssFilter);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(
                "http://192.168.0.150:8080",
                "http://127.0.0.1:8080",
                "http://localhost:8080"
        ));
        cors.setAllowedMethods(List.of("POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/oauth2/**", cors);
        return source;
    }
}
