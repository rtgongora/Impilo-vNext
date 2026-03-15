package zw.gov.mohcc.impilo.offlineedge.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records a break-glass activation — an emergency override of normal
 * authority boundaries (e.g. accessing a patient from a different facility,
 * or continuing after entitlement expiry).
 *
 * <p>Every break-glass activation creates a mandatory review item with a
 * 24-hour deadline.</p>
 */
@Entity
@Table(name = "ofe_break_glass_events")
public class BreakGlassEventEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "facility_id", nullable = false)
    private String facilityId;

    @Column(name = "patient_ref")
    private String patientRef;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** SCOPE_EXTENSION or EXPIRED_ENTITLEMENT */
    @Column(name = "override_type", nullable = false)
    private String overrideType;

    @Column(name = "original_scope")
    private String originalScope;

    @Column(name = "extended_scope")
    private String extendedScope;

    @Column(name = "entitlement_id")
    private UUID entitlementId;

    @Column(name = "action_id")
    private UUID actionId;

    @Column(name = "activated_at", nullable = false)
    private OffsetDateTime activatedAt;

    @Column(name = "review_required_by", nullable = false)
    private OffsetDateTime reviewRequiredBy;

    /** PENDING_REVIEW, APPROVED, ESCALATED, FLAGGED */
    @Column(nullable = false)
    private String status;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (eventId == null) eventId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (activatedAt == null) activatedAt = OffsetDateTime.now();
        if (status == null) status = "PENDING_REVIEW";
        if (reviewRequiredBy == null) reviewRequiredBy = OffsetDateTime.now().plusHours(24);
    }

    // --- Getters and Setters ---
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public String getPatientRef() { return patientRef; }
    public void setPatientRef(String patientRef) { this.patientRef = patientRef; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOverrideType() { return overrideType; }
    public void setOverrideType(String overrideType) { this.overrideType = overrideType; }
    public String getOriginalScope() { return originalScope; }
    public void setOriginalScope(String originalScope) { this.originalScope = originalScope; }
    public String getExtendedScope() { return extendedScope; }
    public void setExtendedScope(String extendedScope) { this.extendedScope = extendedScope; }
    public UUID getEntitlementId() { return entitlementId; }
    public void setEntitlementId(UUID entitlementId) { this.entitlementId = entitlementId; }
    public UUID getActionId() { return actionId; }
    public void setActionId(UUID actionId) { this.actionId = actionId; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public OffsetDateTime getReviewRequiredBy() { return reviewRequiredBy; }
    public void setReviewRequiredBy(OffsetDateTime reviewRequiredBy) { this.reviewRequiredBy = reviewRequiredBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
