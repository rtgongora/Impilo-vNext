package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Plan version — the precise, effective-dated ruleset for a product (spec §9).
 * PUBLISHED versions are immutable; a correction creates a new version.
 */
@Entity
@Table(name = "cv_plan_versions")
public class PlanVersionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "product_ref", nullable = false)
    private UUID productRef;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", columnDefinition = "jsonb")
    private String rulesJson;

    @Column(name = "claims_submission_deadline_days", nullable = false)
    private int claimsSubmissionDeadlineDays = 90;

    @Column(name = "appeal_deadline_days", nullable = false)
    private int appealDeadlineDays = 60;

    @Column(name = "approval_authority", length = 128)
    private String approvalAuthority;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public PlanVersionEntity() {}

    public static PlanVersionEntity draft(UUID tenantId, String podId, UUID productRef,
                                          int versionNumber, String currency) {
        PlanVersionEntity v = new PlanVersionEntity();
        v.id = UUID.randomUUID();
        v.tenantId = tenantId;
        v.podId = podId != null ? podId : "national-spine";
        v.productRef = productRef;
        v.versionNumber = versionNumber;
        if (currency != null && !currency.isBlank()) v.currency = currency;
        return v;
    }

    public boolean isPublished() { return "PUBLISHED".equals(status); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getProductRef() { return productRef; }
    public int getVersionNumber() { return versionNumber; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; this.updatedAt = OffsetDateTime.now(); }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { this.effectiveFrom = v; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getCurrency() { return currency; }
    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String v) { this.rulesJson = v; }
    public int getClaimsSubmissionDeadlineDays() { return claimsSubmissionDeadlineDays; }
    public void setClaimsSubmissionDeadlineDays(int v) { this.claimsSubmissionDeadlineDays = v; }
    public int getAppealDeadlineDays() { return appealDeadlineDays; }
    public void setAppealDeadlineDays(int v) { this.appealDeadlineDays = v; }
    public String getApprovalAuthority() { return approvalAuthority; }
    public void setApprovalAuthority(String v) { this.approvalAuthority = v; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime v) { this.publishedAt = v; }
}
