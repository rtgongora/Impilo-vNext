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

/** Append-only audit of every governed act on a configuration pack. */
@Entity
@Table(name = "regulatory_config_audit_event", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigAuditEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    private UUID organizationId;
    private UUID packId;

    @Column(nullable = false, length = 48)
    private String subjectType;

    private UUID subjectId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(length = 128)
    private String actorHealthId;

    @Column(length = 48)
    private String actorRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode afterState;

    @Column(length = 128)
    private String correlationId;

    @Column(nullable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
    }
}
