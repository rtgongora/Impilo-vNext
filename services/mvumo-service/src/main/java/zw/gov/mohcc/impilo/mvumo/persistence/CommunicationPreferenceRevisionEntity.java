package zw.gov.mohcc.impilo.mvumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only row for a communication / notification / follow-up preference change.
 * Never update rows in place — new row supersedes.
 */
@Entity
@Table(name = "communication_preference_revision", schema = "mvumo")
public class CommunicationPreferenceRevisionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_ref", nullable = false, length = 300)
    private String patientRef;

    @Column(name = "bundle_version", nullable = false)
    private int bundleVersion;

    @Column(name = "lifecycle_state", nullable = false, length = 48)
    private String lifecycleState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    @Column(name = "previous_revision_id")
    private UUID previousRevisionId;

    @Column(name = "actor_ref", length = 300)
    private String actorRef;

    @Column(name = "actor_relationship", length = 80)
    private String actorRelationship;

    @Column(name = "source_channel", length = 64)
    private String sourceChannel;

    @Column(name = "reason_text", columnDefinition = "text")
    private String reasonText;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "review_due_at")
    private Instant reviewDueAt;

    @Column(name = "assurance_method", length = 80)
    private String assuranceMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_metadata", nullable = false, columnDefinition = "jsonb")
    private String deviceMetadata = "{}";

    @Column(name = "audit_ref", length = 64)
    private String auditRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (effectiveAt == null) {
            effectiveAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPatientRef() {
        return patientRef;
    }

    public void setPatientRef(String patientRef) {
        this.patientRef = patientRef;
    }

    public int getBundleVersion() {
        return bundleVersion;
    }

    public void setBundleVersion(int bundleVersion) {
        this.bundleVersion = bundleVersion;
    }

    public String getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(String lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public UUID getPreviousRevisionId() {
        return previousRevisionId;
    }

    public void setPreviousRevisionId(UUID previousRevisionId) {
        this.previousRevisionId = previousRevisionId;
    }

    public String getActorRef() {
        return actorRef;
    }

    public void setActorRef(String actorRef) {
        this.actorRef = actorRef;
    }

    public String getActorRelationship() {
        return actorRelationship;
    }

    public void setActorRelationship(String actorRelationship) {
        this.actorRelationship = actorRelationship;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getReviewDueAt() {
        return reviewDueAt;
    }

    public void setReviewDueAt(Instant reviewDueAt) {
        this.reviewDueAt = reviewDueAt;
    }

    public String getAssuranceMethod() {
        return assuranceMethod;
    }

    public void setAssuranceMethod(String assuranceMethod) {
        this.assuranceMethod = assuranceMethod;
    }

    public String getDeviceMetadata() {
        return deviceMetadata;
    }

    public void setDeviceMetadata(String deviceMetadata) {
        this.deviceMetadata = deviceMetadata;
    }

    public String getAuditRef() {
        return auditRef;
    }

    public void setAuditRef(String auditRef) {
        this.auditRef = auditRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
