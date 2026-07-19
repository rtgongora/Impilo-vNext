package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A facility governance case (FJ6/FJ7/FJ9): a steward-decided change that must
 * not be a self-service write — high-risk field changes, ownership/management
 * transfers, duplicate/fake reports.
 */
@Entity
@Table(name = "facility_governance_case", schema = "tuso")
public class FacilityGovernanceCaseEntity {

    public static final String TYPE_HIGH_RISK_UPDATE = "HIGH_RISK_UPDATE";
    public static final String TYPE_TRANSFER = "TRANSFER";
    public static final String TYPE_DUPLICATE_REPORT = "DUPLICATE_REPORT";
    public static final Set<String> TYPES = Set.of(
            TYPE_HIGH_RISK_UPDATE, TYPE_TRANSFER, TYPE_DUPLICATE_REPORT);

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_MERGED = "MERGED";
    public static final String STATUS_REDIRECTED = "REDIRECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_ref", nullable = false, length = 40)
    private String caseRef;

    @Column(name = "case_type", nullable = false, length = 24)
    private String caseType;

    @Column(name = "facility_uuid", nullable = false)
    private UUID facilityUuid;

    @Column(name = "raised_by", nullable = false, length = 128)
    private String raisedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_OPEN;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCaseRef() { return caseRef; }
    public void setCaseRef(String caseRef) { this.caseRef = caseRef; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public UUID getFacilityUuid() { return facilityUuid; }
    public void setFacilityUuid(UUID facilityUuid) { this.facilityUuid = facilityUuid; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
