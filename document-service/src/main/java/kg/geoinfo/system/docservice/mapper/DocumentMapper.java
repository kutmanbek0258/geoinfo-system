package kg.geoinfo.system.docservice.mapper;

import kg.geoinfo.system.docservice.dto.DocumentDto;
import kg.geoinfo.system.docservice.dto.TagDto;
import kg.geoinfo.system.docservice.models.Document;
import kg.geoinfo.system.docservice.models.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    DocumentMapper INSTANCE = Mappers.getMapper(DocumentMapper.class);

    DocumentDto toDto(Document document);

    TagDto toDto(Tag tag);

    List<DocumentDto> toDto(List<Document> documents);

    Set<TagDto> toDto(Set<Tag> tags);
}
