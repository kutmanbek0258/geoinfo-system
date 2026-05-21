package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.GeoFolderDto;
import java.util.List;
import java.util.UUID;

public interface GeoFolderService {
    GeoFolderDto create(GeoFolderDto dto);
    GeoFolderDto update(UUID id, GeoFolderDto dto);
    void delete(UUID id);
    GeoFolderDto getById(UUID id);
    List<GeoFolderDto> getByProject(UUID projectId);
}
