package kg.geoinfo.system.authservice.service.impl;

import kg.geoinfo.system.authservice.exception.ServiceException;
import kg.geoinfo.system.authservice.service.ResourceServerService;
import kg.geoinfo.system.authservice.service.UserService;
import kg.geoinfo.system.authservice.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultResourceServerService implements ResourceServerService {

    private final UserService userService;

    @Override
    public ResponseEntity<byte[]> getUserAvatar(UUID userId) {
        try {
            UserService.UserAvatar userAvatar = userService.getUserAvatar(userId);
            return HttpUtils.appendFileToResponse(
                userAvatar.storeDto().getId().toString(),
                userAvatar.storeDto().getContentType(),
                userAvatar.avatar()
            );
        } catch (ServiceException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
