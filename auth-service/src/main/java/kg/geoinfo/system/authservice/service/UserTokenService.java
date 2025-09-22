package kg.geoinfo.system.authservice.service;

import kg.geoinfo.system.authservice.dto.UserTokenInfoDto;

import java.util.List;

public interface UserTokenService {

    List<UserTokenInfoDto> getUserTokens();

    void recallToken(String authenticationId);

    void recallAllCurrentUserTokens();
}
