package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A patient's selection of one offer and its commitment record (OF-B6/OF-B11,
 * Vol II §11.7/§8.9 — {@code mf_selections}).
 *
 * <p>RC-1: at most one non-FAILED selection per request (partial unique index
 * in Postgres, re-enforced in code for H2); retries are idempotent on
 * ({@code tenantId}, {@code idempotencyKey}). {@code stepLogJson} is the
 * append-only §8.9 twelve-step commitment log.</p>
 */
@Entity
@Table(name = "mf_selections")
public class SelectionEntity {

    @Id
    @Column(name = "selection_id", nullable = false, length = 26)
    private String selectionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false, length = 26)
    private String requestId;

    @Column(name = "offer_id", nullable = false, length = 26)
    private String offerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SelectionStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    /** OROS prescription claim id (commitment step 6, medication profiles). */
    @Column(name = "prescription_claim_id", length = 64)
    private String prescriptionClaimId;

    /** Discrete coded outcome on failure (RC-2/3/6/7 family). */
    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_log_json", columnDefinition = "jsonb")
    private String stepLogJson;

    /** Fulfilment dispatch reference once the step-11 seam is wired (RC-8 idempotent on selection). */
    @Column(name = "dispatch_ref", length = 64)
    private String dispatchRef;

    @Column(name = "committed_at")
    private OffsetDateTime committedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getSelectionId() { return selectionId; }
    public void setSelectionId(String selectionId) { this.selectionId = selectionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public SelectionStatus getStatus() { return status; }
    public void setStatus(SelectionStatus status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getPrescriptionClaimId() { return prescriptionClaimId; }
    public void setPrescriptionClaimId(String prescriptionClaimId) { this.prescriptionClaimId = prescriptionClaimId; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    public String getStepLogJson() { return stepLogJson; }
    public void setStepLogJson(String stepLogJson) { this.stepLogJson = stepLogJson; }

    public String getDispatchRef() { return dispatchRef; }
    public void setDispatchRef(String dispatchRef) { this.dispatchRef = dispatchRef; }

    public OffsetDateTime getCommittedAt() { return committedAt; }
    public void setCommittedAt(OffsetDateTime committedAt) { this.committedAt = committedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
