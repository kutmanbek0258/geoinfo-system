package kg.geoinfo.system.authservice.mapper;

import kg.geoinfo.system.authservice.dao.entity.UserEventEntity;
import kg.geoinfo.system.authservice.dto.UserEventDto;
import kg.geoinfo.system.authservice.service.MessageService;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserEventMapper {

    public UserEventDto map(UserEventEntity entity, MessageService messageService) {
        return UserEventDto.builder()
            .id(entity.getId())
            .eventType(entity.getEventType())
            .eventTypeName(messageService.getMessage(entity.getEventType()))
            .ipAddress(entity.getIpAddress())
            .clientId(entity.getClientId())
            .browser(entity.getBrowser())
            .device(entity.getDevice())
            .os(entity.getOs())
            .creationDate(entity.getCreationDate())
            .build();
    }
}
