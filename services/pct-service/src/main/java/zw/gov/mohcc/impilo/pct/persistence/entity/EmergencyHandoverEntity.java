package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The acceptance handshake (pct V204). Responsibility transfers only when the accepting party
 * writes back with its own {@code accepting_ref} — never on a request, a page, or a timeout.
 */
@Entity
@Table(name = "emergency_handover")
public class EmergencyHandoverEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "handover_id")
    private UUID handoverId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_service")
    private String targetService;

    @Column(name = "target_facility_id")
    private UUID targetFacilityId;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "request_reason", columnDefinition = "TEXT")
    private String requestReason;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "accepted_by")
    private String acceptedBy;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    /** The accepting party's OWN record id. The invariant. */
    @Column(name = "accepting_ref")
    private String acceptingRef;

    @Column(name = "accepting_service")
    private String acceptingService;

    @Column(name = "declined_by")
    private String declinedBy;

    @Column(name = "declined_at")
    private OffsetDateTime declinedAt;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "expired_by")
    private String expiredBy;

    /** Link only — rito owns the safety case. */
    @Column(name = "rito_case_ref")
    private String ritoCaseRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (requestedAt == null) requestedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID handoverId) { this.handoverId = handoverId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }
    public UUID getTargetFacilityId() { return targetFacilityId; }
    public void setTargetFacilityId(UUID targetFacilityId) { this.targetFacilityId = targetFacilityId; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getRequestReason() { return requestReason; }
    public void setRequestReason(String requestReason) { this.requestReason = requestReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public String getAcceptingRef() { return acceptingRef; }
    public void setAcceptingRef(String acceptingRef) { this.acceptingRef = acceptingRef; }
    public String getAcceptingService() { return acceptingService; }
    public void setAcceptingService(String acceptingService) { this.acceptingService = acceptingService; }
    public String getDeclinedBy() { return declinedBy; }
    public void setDeclinedBy(String declinedBy) { this.declinedBy = declinedBy; }
    public OffsetDateTime getDeclinedAt() { return declinedAt; }
    public void setDeclinedAt(OffsetDateTime declinedAt) { this.declinedAt = declinedAt; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
    public OffsetDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(OffsetDateTime expiredAt) { this.expiredAt = expiredAt; }
    public String getExpiredBy() { return expiredBy; }
    public void setExpiredBy(String expiredBy) { this.expiredBy = expiredBy; }
    public String getRitoCaseRef() { return ritoCaseRef; }
    public void setRitoCaseRef(String ritoCaseRef) { this.ritoCaseRef = ritoCaseRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
