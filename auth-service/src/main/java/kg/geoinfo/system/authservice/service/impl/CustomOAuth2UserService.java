package kg.geoinfo.system.authservice.service.impl;

import kg.geoinfo.system.authservice.service.AuthProviderService;
import kg.geoinfo.system.authservice.type.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String clientRegId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.findByName(clientRegId);
        return authProviderService.saveAndMap(oAuth2User, provider);
    }
}
