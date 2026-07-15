package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A report against social content. SELF_HARM/ABUSE/safeguarding reasons trigger a care linkage. */
@Entity
@Table(name = "simba_content_report", schema = "simba")
public class ContentReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, unique = true)
    private UUID reportId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reporter_cpid", nullable = false, length = 128)
    private String reporterCpid;

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "subject_owner_cpid", length = 128)
    private String subjectOwnerCpid;

    @Column(name = "reason", nullable = false, length = 24)
    private String reason;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "care_linkage_id")
    private UUID careLinkageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (reportId == null) {
            reportId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getReportId() { return reportId; }
    public void setReportId(UUID reportId) { this.reportId = reportId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getReporterCpid() { return reporterCpid; }
    public void setReporterCpid(String reporterCpid) { this.reporterCpid = reporterCpid; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public String getSubjectOwnerCpid() { return subjectOwnerCpid; }
    public void setSubjectOwnerCpid(String subjectOwnerCpid) { this.subjectOwnerCpid = subjectOwnerCpid; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCareLinkageId() { return careLinkageId; }
    public void setCareLinkageId(UUID careLinkageId) { this.careLinkageId = careLinkageId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
