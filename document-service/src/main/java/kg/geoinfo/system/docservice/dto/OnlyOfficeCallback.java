package kg.geoinfo.system.docservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для парсинга callback-пейлоада OnlyOffice
 * Документация: https://api.onlyoffice.com/editors/callback
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyOfficeCallback {
    private int status;
    private String url; // ссылка на изменённый файл (если status == 2 или 6)
    @JsonProperty("key")
    private String key; // уникальный ключ документа
    // можно добавить другие поля из payload OnlyOffice при необходимости
}