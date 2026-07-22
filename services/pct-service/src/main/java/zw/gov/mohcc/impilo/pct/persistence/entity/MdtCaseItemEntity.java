package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * TM-B15 (B15-1): one agenda item on an MDT board — a presented referral with its board
 * recommendation and per-participant consensus/dissent positions (V051). The ordinal doubles
 * as the pseudonym ("Case 1", "Case 2"…) under pseudonymised presentation.
 */
@Entity
@Table(name = "pct_mdt_case_items")
public class MdtCaseItemEntity {

    @Id
    @Column(name = "case_item_id")
    private UUID caseItemId;

    @Column(name = "mdt_session_id", nullable = false)
    private UUID mdtSessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "presenter_id")
    private String presenterId;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "recommendation")
    private String recommendation;

    // consensus + dissent record: [{participant, position AGREE|DISSENT, note}]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "positions", columnDefinition = "jsonb")
    private String positions = "[]";

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getCaseItemId() { return caseItemId; }
    public void setCaseItemId(UUID caseItemId) { this.caseItemId = caseItemId; }
    public UUID getMdtSessionId() { return mdtSessionId; }
    public void setMdtSessionId(UUID mdtSessionId) { this.mdtSessionId = mdtSessionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID referralId) { this.referralId = referralId; }
    public String getPresenterId() { return presenterId; }
    public void setPresenterId(String presenterId) { this.presenterId = presenterId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getPositions() { return positions; }
    public void setPositions(String positions) { this.positions = positions; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
