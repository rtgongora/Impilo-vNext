package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudKind;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudSeverity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mushex_fraud_flags")
public class FraudFlagEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private FraudKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private FraudSeverity severity;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "evidence", columnDefinition = "jsonb")
    private String evidence;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public FraudKind getKind() {
        return kind;
    }

    public void setKind(FraudKind kind) {
        this.kind = kind;
    }

    public FraudSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(FraudSeverity severity) {
        this.severity = severity;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
