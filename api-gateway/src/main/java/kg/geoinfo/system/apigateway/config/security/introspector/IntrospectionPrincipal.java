package kg.geoinfo.system.apigateway.config.security.introspector;

import kg.geoinfo.system.apigateway.dto.AuthorizedUser;
import kg.geoinfo.system.apigateway.dto.TokenInfoDto;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.util.*;

public class IntrospectionPrincipal extends TokenInfoOAuth2ClaimAccessor implements OAuth2AuthenticatedPrincipal {

    private final TokenInfoDto tokenInfo;
    private final AuthorizedUser authorizedUser;

    private static final String AUTHORITY_PREFIX = "SCOPE_";

    public IntrospectionPrincipal(TokenInfoDto tokenInfo) {
        this.tokenInfo = tokenInfo;
        this.authorizedUser = AuthorizedUser.build(tokenInfo.getPrincipal());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.emptyMap();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (authorizedUser != null) {
            authorities.addAll(authorizedUser.getAuthorities());
        }
        if (tokenInfo.getScopes() != null) {
            tokenInfo.getScopes().forEach(scope ->
                    authorities.add(new SimpleGrantedAuthority(AUTHORITY_PREFIX + scope)));
        }
        return authorities;
    }

    @Override
    public String getName() {
        if (authorizedUser == null) {
            return tokenInfo.getClientId();
        }
        return authorizedUser.getName();
    }

    @Override
    TokenInfoDto getTokenInfo() {
        return this.tokenInfo;
    }
}