package kg.geoinfo.system.docservice.config.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;
import kg.geoinfo.system.docservice.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OnlyOfficeJwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RequestMatcher requestMatcher = new AntPathRequestMatcher("/api/documents/onlyoffice-callback/**");

    @Value("${onlyoffice.jwt-secret}")
    private String jwtSecret;

    public static final String ONLYOFFICE_CALLBACK_ATTRIBUTE = "onlyOfficeCallback";

    public OnlyOfficeJwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Only apply this filter to the matched callback URL
        if (!this.requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header for OnlyOffice callback");
            return;
        }

        String token = header.substring(7);

        try {
            OnlyOfficeCallback callbackPayload = jwtUtil.parseCallbackToken(token, jwtSecret);
            request.setAttribute(ONLYOFFICE_CALLBACK_ATTRIBUTE, callbackPayload);
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
