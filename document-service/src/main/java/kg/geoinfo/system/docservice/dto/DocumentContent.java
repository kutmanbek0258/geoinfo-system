package kg.geoinfo.system.docservice.dto;

import kg.geoinfo.system.docservice.models.Document;

public record DocumentContent(Document document, byte[] content) {
}
