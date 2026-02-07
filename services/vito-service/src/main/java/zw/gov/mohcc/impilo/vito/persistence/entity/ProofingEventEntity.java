package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.vito.core.ProofingMethod;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "proofing_event", schema = "vito")
public class ProofingEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private ProofingMethod method;

    @Column(name = "assurance_level")
    private int assuranceLevel;

    @Column(name = "artifact_refs", columnDefinition = "jsonb")
    private String artifactRefs;

    @Column(name = "verifier_id")
    private String verifierId;

    @Column(name = "status", nullable = false)
    private String status = "COMPLETED";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public ProofingMethod getMethod() { return method; }
    public void setMethod(ProofingMethod method) { this.method = method; }
    public int getAssuranceLevel() { return assuranceLevel; }
    public void setAssuranceLevel(int assuranceLevel) { this.assuranceLevel = assuranceLevel; }
    public String getArtifactRefs() { return artifactRefs; }
    public void setArtifactRefs(String artifactRefs) { this.artifactRefs = artifactRefs; }
    public String getVerifierId() { return verifierId; }
    public void setVerifierId(String verifierId) { this.verifierId = verifierId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
