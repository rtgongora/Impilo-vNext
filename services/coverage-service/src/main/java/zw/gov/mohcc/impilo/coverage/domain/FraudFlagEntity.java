package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Fraud/waste/abuse flag (spec §25). Automated flag -> governed review. */
@Entity
@Table(name = "cv_fraud_flags")
public class FraudFlagEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "subject_type", nullable = false, length = 32) private String subjectType;
    @Column(name = "subject_ref", nullable = false, length = 255) private String subjectRef;
    @Column(name = "flag_type", nullable = false, length = 48) private String flagType;
    @Column(name = "severity", nullable = false, length = 16) private String severity = "MEDIUM";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "detail", columnDefinition = "jsonb") private String detail;
    @Column(name = "status", nullable = false, length = 16) private String status = "OPEN";
    @Column(name = "reviewer_id", length = 128) private String reviewerId;
    @Column(name = "resolution", length = 255) private String resolution;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public FraudFlagEntity() {}

    public static FraudFlagEntity create(UUID tenantId, String podId, String subjectType, String subjectRef,
                                         String flagType, String severity, String detail) {
        FraudFlagEntity f = new FraudFlagEntity();
        f.id = UUID.randomUUID();
        f.tenantId = tenantId;
        f.podId = podId != null ? podId : "national-spine";
        f.subjectType = subjectType;
        f.subjectRef = subjectRef;
        f.flagType = flagType;
        if (severity != null && !severity.isBlank()) f.severity = severity;
        f.detail = detail;
        return f;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectRef() { return subjectRef; }
    public String getFlagType() { return flagType; }
    public String getSeverity() { return severity; }
    public String getDetail() { return detail; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; this.updatedAt = OffsetDateTime.now(); }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String v) { this.reviewerId = v; }
    public String getResolution() { return resolution; }
    public void setResolution(String v) { this.resolution = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
