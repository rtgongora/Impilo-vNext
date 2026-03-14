package zw.gov.mohcc.impilo.offlineedge.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conflict review queue entry for offline replay conflicts.
 *
 * <p>When a replayed action results in a conflict (e.g. duplicate Observation
 * in BUTANO), the conflict is queued here for manual resolution.</p>
 */
@Entity
@Table(name = "ofe_conflict_reviews")
public class ConflictReviewEntity {

    @Id
    @Column(name = "conflict_id")
    private UUID conflictId;

    @Column(name = "action_id", nullable = false)
    private UUID actionId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_ref", nullable = false)
    private String patientRef;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "conflict_reason", nullable = false)
    private String conflictReason;

    @Column(name = "offline_payload", columnDefinition = "TEXT")
    private String offlinePayload;

    @Column(name = "existing_resource_ref")
    private String existingResourceRef;

    @Column(nullable = false)
    private String status; // PENDING, RESOLVED_KEEP_OFFLINE, RESOLVED_KEEP_EXISTING, RESOLVED_MERGED

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        if (conflictId == null) conflictId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = "PENDING";
    }

    // --- Getters and Setters ---
    public UUID getConflictId() { return conflictId; }
    public void setConflictId(UUID conflictId) { this.conflictId = conflictId; }
    public UUID getActionId() { return actionId; }
    public void setActionId(UUID actionId) { this.actionId = actionId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPatientRef() { return patientRef; }
    public void setPatientRef(String patientRef) { this.patientRef = patientRef; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getConflictReason() { return conflictReason; }
    public void setConflictReason(String conflictReason) { this.conflictReason = conflictReason; }
    public String getOfflinePayload() { return offlinePayload; }
    public void setOfflinePayload(String offlinePayload) { this.offlinePayload = offlinePayload; }
    public String getExistingResourceRef() { return existingResourceRef; }
    public void setExistingResourceRef(String existingResourceRef) { this.existingResourceRef = existingResourceRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
