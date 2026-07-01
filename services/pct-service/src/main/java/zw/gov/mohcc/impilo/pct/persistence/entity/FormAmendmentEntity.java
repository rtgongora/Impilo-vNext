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

/** Append-only amendment log entry for a submitted form response. */
@Entity
@Table(name = "pct_form_amendment")
public class FormAmendmentEntity {

    @Id
    @Column(name = "amendment_id", nullable = false)
    private UUID amendmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prior_answers", nullable = false, columnDefinition = "jsonb")
    private String priorAnswers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_answers", nullable = false, columnDefinition = "jsonb")
    private String newAnswers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_link_ids", nullable = false, columnDefinition = "jsonb")
    private String changedLinkIds = "[]";

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "amended_by", nullable = false)
    private String amendedBy;

    @Column(name = "amended_at", nullable = false, updatable = false)
    private OffsetDateTime amendedAt;

    @PrePersist
    protected void onCreate() {
        if (amendmentId == null) {
            amendmentId = UUID.randomUUID();
        }
        if (amendedAt == null) {
            amendedAt = OffsetDateTime.now();
        }
    }

    public UUID getAmendmentId() { return amendmentId; }
    public void setAmendmentId(UUID amendmentId) { this.amendmentId = amendmentId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getResponseId() { return responseId; }
    public void setResponseId(UUID responseId) { this.responseId = responseId; }

    public String getPriorAnswers() { return priorAnswers; }
    public void setPriorAnswers(String priorAnswers) { this.priorAnswers = priorAnswers; }

    public String getNewAnswers() { return newAnswers; }
    public void setNewAnswers(String newAnswers) { this.newAnswers = newAnswers; }

    public String getChangedLinkIds() { return changedLinkIds; }
    public void setChangedLinkIds(String changedLinkIds) { this.changedLinkIds = changedLinkIds; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getAmendedBy() { return amendedBy; }
    public void setAmendedBy(String amendedBy) { this.amendedBy = amendedBy; }

    public OffsetDateTime getAmendedAt() { return amendedAt; }
}
