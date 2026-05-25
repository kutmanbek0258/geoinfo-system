package kg.geoinfo.system.geoprintservice.model;

import jakarta.persistence.*;
import kg.geoinfo.system.geoprintservice.config.audit.AuditableCustom;
import kg.geoinfo.system.geoprintservice.enums.PrintTaskStatus;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(schema = "print", name = "print_tasks")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PrintTask extends AuditableCustom<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrintTaskStatus status;

    @Column(nullable = false)
    private String layout;

    @Column(name = "s3_url")
    private String s3Url;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attributes;
}
