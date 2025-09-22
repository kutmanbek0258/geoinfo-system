package kg.geoinfo.system.authservice.service;

import kg.geoinfo.system.authservice.dto.ReferenceDto;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Collection;
import java.util.List;

public interface ReferenceService {

    List<ReferenceDto<String>> getAuthMethods();

    List<ReferenceDto<String>> getGrantTypes();

    List<ReferenceDto<String>> getScopes();

    String getGrantTypeName(AuthorizationGrantType grantType);

    String getScopeName(String scope);

    List<String> getScopeNames(Collection<String> scopeCodes);
}
