package kg.geoinfo.system.docservice.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OnlyOfficeConfig {
    private Document document;
    private EditorConfig editorConfig;
    private String token;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private String title;
        private String url;
        private String fileType;
        private String key;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EditorConfig {
        private String mode; // "view" or "edit"
        private String callbackUrl;
        private String userId;
        private String userName;
    }
}