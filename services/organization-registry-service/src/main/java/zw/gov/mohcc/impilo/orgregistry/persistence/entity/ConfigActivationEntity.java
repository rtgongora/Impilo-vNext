package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The append-only record that a release went live, carrying the validation report that justified
 * it. The report is stored rather than recomputed because the question a regulator asks later is
 * "what did we check before we activated this?", and re-running today's validator against today's
 * data does not answer it.
 */
@Entity
@Table(name = "regulatory_config_activation", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigActivationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID packId;

    @Column(nullable = false)
    private UUID releaseId;

    private UUID previousReleaseId;

    @Column(nullable = false, length = 128)
    private String activatedBy;

    @Column(nullable = false)
    private OffsetDateTime activatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode validationReport;

    @Column(length = 128)
    private String correlationId;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (activatedAt == null) {
            activatedAt = OffsetDateTime.now();
        }
    }
}
