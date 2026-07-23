package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A Nompilo service advisory (V014) — a data-driven platform message (launch notice,
 * planned-disruption notice, incident alert, or call to action) surfaced by the
 * experience shell. Advisories are DATA, never hard-coded in the frontend.
 *
 * <p>{@code targeting} and {@code primaryActions} are stored as JSONB strings and
 * interpreted in {@code AdvisoryResolveService}. The public resolve lane returns only
 * status=PUBLISHED, in-window, audience='public' advisories with allow-listed fields
 * (no PII). {@code approvedBy} must be set before an EMERGENCY_ALERT or
 * IMPORTANT_DISRUPTION advisory may be PUBLISHED (enforced in the admin service).</p>
 */
@Entity
@Table(name = "service_advisory", schema = "guidance")
public class ServiceAdvisoryEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId = "national-spine";
    @Column(nullable = false, length = 512) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;

    /**
     * Notice category (V017 Notices &amp; Bulletins). SERVICE_STATUS | PUBLIC_HEALTH_CAMPAIGN
     * | SAFETY_RECALL | REGULATORY_NOTICE | DISASTER_ALERT | PROCUREMENT_NOTICE. Legacy
     * service advisories default to SERVICE_STATUS. SAFETY_RECALL and REGULATORY_NOTICE
     * additionally require {@code approvedBy} before PUBLISHED (admin service).
     */
    @Column(nullable = false, length = 32) private String category = "SERVICE_STATUS";

    /** INFORMATIONAL | SERVICE_NOTICE | IMPORTANT_DISRUPTION | EMERGENCY_ALERT */
    @Column(nullable = false, length = 32) private String severity;
    /** MODAL | BANNER | BUBBLE | INLINE | EMERGENCY */
    @Column(nullable = false, length = 16) private String variant;
    /** DRAFT | SCHEDULED | PUBLISHED | WITHDRAWN | EXPIRED */
    @Column(nullable = false, length = 16) private String status = "DRAFT";

    /** JSONB selectors: audience/deviceType/language/province/district/serviceKey/appKey/facilityId/journeyStage/incidentRef/releaseRef. */
    @Column(columnDefinition = "JSONB") private String targeting;
    @Column(name = "starts_at") private OffsetDateTime startsAt;
    @Column(name = "expires_at") private OffsetDateTime expiresAt;
    /** JSONB array of { label, href } call-to-action objects. */
    @Column(name = "primary_actions", columnDefinition = "JSONB") private String primaryActions;

    @Column(name = "created_by", length = 255) private String createdBy;
    @Column(name = "approved_by", length = 255) private String approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTargeting() { return targeting; }
    public void setTargeting(String targeting) { this.targeting = targeting; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getPrimaryActions() { return primaryActions; }
    public void setPrimaryActions(String primaryActions) { this.primaryActions = primaryActions; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
