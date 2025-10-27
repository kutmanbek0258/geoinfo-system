package kg.geoinfo.system.authservice.service.impl;

import kg.geoinfo.system.authservice.service.AuthProviderService;
import kg.geoinfo.system.authservice.type.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthProviderService authProviderService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // базовая загрузка пользователя
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String clientRegId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.findByName(clientRegId);

        // Если Entra ID или другой OIDC провайдер — адаптируем в DefaultOAuth2User
        if (oAuth2User instanceof OidcUser oidcUser) {
            // определяем ключевой атрибут для имени (Entra ID использует preferred_username)
            String nameAttributeKey = "preferred_username";
            if (!oidcUser.getAttributes().containsKey(nameAttributeKey)) {
                // fallback: Google и другие провайдеры
                nameAttributeKey = userRequest
                        .getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName();
            }

            oAuth2User = new DefaultOAuth2User(
                    oidcUser.getAuthorities(),
                    oidcUser.getAttributes(),
                    nameAttributeKey
            );
        }
        return authProviderService.saveAndMap(oAuth2User, provider);
    }
}
