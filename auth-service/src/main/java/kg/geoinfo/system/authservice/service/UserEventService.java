package kg.geoinfo.system.authservice.service;

import jakarta.servlet.http.HttpServletRequest;
import kg.geoinfo.system.authservice.dao.type.UserEventType;
import kg.geoinfo.system.authservice.dto.PageableResponseDto;
import kg.geoinfo.system.authservice.dto.UserEventDto;

public interface UserEventService {

    PageableResponseDto<UserEventDto> searchEvents(int page, int pageSize);

    void createEvent(UserEventType eventType, String clientId, HttpServletRequest request);

    /**
     * Удалить события пользователя, являющиеся устаревшими.
     */
    void deleteOldEvents();
}
