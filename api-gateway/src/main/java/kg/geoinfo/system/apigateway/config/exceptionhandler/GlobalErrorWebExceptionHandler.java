package kg.geoinfo.system.apigateway.config.exceptionhandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // Очень низкий приоритет, чтобы Spring Security обрабатывал свои ошибки
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {
    private final ObjectMapper objectMapper; // Инжектируйте ObjectMapper

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "Internal Server Error";

        if (ex instanceof AuthenticationException || ex instanceof AccessDeniedException) {
            // Эти ошибки должны обрабатываться Spring Security,
            // но если они как-то дошли сюда, мы можем их явно обработать.
            status = (ex instanceof AuthenticationException) ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
            errorMessage = ex.getMessage();
            // Spring Security обычно сам добавляет WWW-Authenticate заголовок
        } else if (ex instanceof WebClientResponseException) {
            WebClientResponseException wcEx = (WebClientResponseException) ex;
            status = (HttpStatus) wcEx.getStatusCode();
            errorMessage = wcEx.getResponseBodyAsString();
        } else {
            log.error("Unhandled error: {}", ex.getMessage(), ex);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = new ErrorResponse(status.value(), status.getReasonPhrase(), errorMessage);
        try {
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(errorResponse)))
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to write error response", e);
            return Mono.error(e);
        }
    }
}

// Вспомогательный класс для тела ошибки
record ErrorResponse(int status, String error, String message) {}