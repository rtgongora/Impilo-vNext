package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_claims")
public class ClaimEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "coverage_id", nullable = false)
    private UUID coverageId;

    @Column(name = "preauth_id")
    private UUID preauthId;

    @Column(name = "facility_id", nullable = false, length = 255)
    private String facilityId;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(name = "encounter_id", length = 255)
    private String encounterId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "claim_type", nullable = false, length = 32)
    private String claimType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", nullable = false, columnDefinition = "jsonb")
    private String lineItems;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjudication", columnDefinition = "jsonb")
    private String adjudication;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "adjudicated_at")
    private OffsetDateTime adjudicatedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ClaimEntity() {}

    public ClaimEntity(UUID tenantId, String podId, UUID coverageId, UUID preauthId,
                       String facilityId, String providerId, String encounterId,
                       String claimType, String lineItems, BigDecimal totalAmount) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId;
        this.coverageId = coverageId;
        this.preauthId = preauthId;
        this.facilityId = facilityId;
        this.providerId = providerId;
        this.encounterId = encounterId;
        this.claimType = claimType;
        this.lineItems = lineItems;
        this.totalAmount = totalAmount;
        this.status = "SUBMITTED";
        OffsetDateTime now = OffsetDateTime.now();
        this.submittedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getCoverageId() { return coverageId; }
    public UUID getPreauthId() { return preauthId; }
    public String getFacilityId() { return facilityId; }
    public String getProviderId() { return providerId; }
    public String getEncounterId() { return encounterId; }
    public String getStatus() { return status; }
    public String getClaimType() { return claimType; }
    public String getLineItems() { return lineItems; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public String getAdjudication() { return adjudication; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public OffsetDateTime getAdjudicatedAt() { return adjudicatedAt; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Marks claim adjudicated (e.g. from MUSHEX feedback). */
    public void markAdjudicated(BigDecimal approvedAmount, String adjudicationJson) {
        this.status = "ADJUDICATED";
        this.approvedAmount = approvedAmount;
        this.adjudication = adjudicationJson;
        this.adjudicatedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /** Marks claim paid after insurer settlement. */
    public void markPaid(BigDecimal amountPaid) {
        this.status = "PAID";
        if (amountPaid != null) {
            this.approvedAmount = amountPaid;
        }
        this.paidAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
}
