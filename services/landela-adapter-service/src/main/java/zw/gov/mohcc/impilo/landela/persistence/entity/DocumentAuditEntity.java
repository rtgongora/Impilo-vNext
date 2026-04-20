package zw.gov.mohcc.impilo.landela.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_audit", schema = "landela_adapter")
public class DocumentAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "actor_type", length = 50)
    private String actorType;

    @Column(name = "purpose_of_use", length = 100)
    private String purposeOfUse;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "details", columnDefinition = "jsonb")
    private String details = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
