package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The accepting side of a psychiatric emergency handover. See V001__init.sql for the full
 * rationale — one row per pct {@code emergency_handover} (target_type=MENTAL_HEALTH).
 */
@Entity
@Table(name = "mh_referral")
public class ReferralEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "handover_id", nullable = false)
    private UUID handoverId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "request_reason")
    private String requestReason;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID v) { this.handoverId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRequestReason() { return requestReason; }
    public void setRequestReason(String v) { this.requestReason = v; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String v) { this.requestedBy = v; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime v) { this.requestedAt = v; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String v) { this.reviewedBy = v; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime v) { this.reviewedAt = v; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String v) { this.declineReason = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
