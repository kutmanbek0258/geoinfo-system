package kg.geoinfo.system.authservice.service;

import kg.geoinfo.system.authservice.dto.AdminUserDto;
import kg.geoinfo.system.authservice.dto.PageableResponseDto;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

public interface AdminUserService {

    PageableResponseDto<AdminUserDto> searchUsers(int page, int pageSize, String email);

    void assignAdmin(String email);

    void dismissAdmin(UUID userId);

    ResponseEntity<byte[]> getAvatar(UUID avatarFileId);
}
