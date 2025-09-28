package kg.geoinfo.system.docservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.docservice.config.audit.AuditableCustom;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "documents", schema = "documents", indexes = {
        @Index(name = "ix_documents_geo_object_id", columnList = "geo_object_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document extends AuditableCustom<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "geo_object_id", nullable = false)
    private UUID geoObjectId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "minio_object_key", nullable = false, unique = true)
    private String minioObjectKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_latest_version")
    @Builder.Default
    private boolean isLatestVersion = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "document_tag_link",
            schema = "documents",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags;
}
