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
        private User user;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class User {
            private String id;
            private String name;
        }
    }
}