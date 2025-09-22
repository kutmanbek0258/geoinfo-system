package kg.geoinfo.system.authservice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kg.geoinfo.system.authservice.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface AccountService {

    UserDto getCurrentUser();

    UserDto save(UserDto dto, MultipartFile avatarFile, HttpServletRequest request, HttpServletResponse response);

    void deleteCurrentUser(HttpServletRequest request, HttpServletResponse response);

    ResponseEntity<byte[]> getAvatarCurrentUser();
}
