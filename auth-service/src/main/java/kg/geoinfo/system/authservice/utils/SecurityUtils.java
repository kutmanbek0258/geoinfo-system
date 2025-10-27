package kg.geoinfo.system.authservice.utils;

import kg.geoinfo.system.authservice.dto.security.AuthorizedUser;
import kg.geoinfo.system.authservice.exception.ServiceException;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class SecurityUtils {

    /**
     * Получить информацию об авторизованном пользователе из контекста безопасности.
     */
    public AuthorizedUser getAuthUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw ServiceException.builder("Authentication is null").build();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AuthorizedUser authorizedUser) {
            return authorizedUser;
        }

        if (principal instanceof OidcUser oidcUser) {
            return mapOidcUser(oidcUser);
        }

        if (principal instanceof OAuth2User oauth2User) {
            return mapOAuth2User(oauth2User);
        }

        throw ServiceException.builder(
                "Principal class = " + principal.getClass().getSimpleName() + " is not supported"
        ).build();
    }

    private AuthorizedUser mapOidcUser(OidcUser oidcUser) {
        Map<String, Object> attrs = oidcUser.getAttributes();

        String email = firstNonNull(
                (String) attrs.get("preferred_username"),
                (String) attrs.get("email"),
                (String) attrs.get("upn")
        );
        String firstName = (String) attrs.getOrDefault("given_name", "");
        String lastName = (String) attrs.getOrDefault("family_name", "");

        List<SimpleGrantedAuthority> authorities = oidcUser.getAuthorities()
                .stream()
                .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                .collect(Collectors.toList());

        return AuthorizedUser.builder(email, "", authorities)
                .firstName(firstName)
                .lastName(lastName)
                .oauthAttributes(attrs)
                .build();
    }

    private AuthorizedUser mapOAuth2User(OAuth2User oauth2User) {
        Map<String, Object> attrs = oauth2User.getAttributes();

        String email = firstNonNull(
                (String) attrs.get("email"),
                (String) attrs.get("preferred_username"),
                (String) attrs.get("login")
        );
        String firstName = (String) attrs.getOrDefault("given_name", "");
        String lastName = (String) attrs.getOrDefault("family_name", "");

        List<SimpleGrantedAuthority> authorities = oauth2User.getAuthorities()
                .stream()
                .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                .collect(Collectors.toList());

        return AuthorizedUser.builder(email, "", authorities)
                .firstName(firstName)
                .lastName(lastName)
                .oauthAttributes(attrs)
                .build();
    }

    private <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

}
