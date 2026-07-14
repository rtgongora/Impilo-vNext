package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Histopathology report (spec §11) — a structured diagnostic narrative captured as a child of a
 * clinical order/result. Carries the pathology sections (gross / microscopic / diagnosis /
 * ancillary tests) and a pathologist sign-out gate. On sign-out a FINAL {@link ResultEntity} is
 * produced through the versioned reporting lifecycle and its identifiers stored back here.
 */
@Entity
@Table(name = "oros_histopath_report")
public class HistopathReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "result_id")
    private UUID resultId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "specimen_ref", length = 128)
    private String specimenRef;

    @Column(name = "gross_description", columnDefinition = "TEXT")
    private String grossDescription;

    @Column(name = "microscopic_description", columnDefinition = "TEXT")
    private String microscopicDescription;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ancillary_tests", columnDefinition = "jsonb")
    private String ancillaryTests;

    @Column(name = "reporting_pathologist", length = 128)
    private String reportingPathologist;

    @Column(name = "sign_out_status", nullable = false, length = 16)
    private String signOutStatus = "DRAFT";

    @Column(name = "signed_out_at")
    private OffsetDateTime signedOutAt;

    @Column(name = "critical", nullable = false)
    private boolean critical = false;

    @Column(name = "critical_reason", columnDefinition = "TEXT")
    private String criticalReason;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_by", length = 128)
    private String acknowledgedBy;

    @Column(name = "butano_report_ref", length = 128)
    private String butanoReportRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getReportId() { return reportId; }
    public void setReportId(UUID reportId) { this.reportId = reportId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public UUID getResultId() { return resultId; }
    public void setResultId(UUID resultId) { this.resultId = resultId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSpecimenRef() { return specimenRef; }
    public void setSpecimenRef(String specimenRef) { this.specimenRef = specimenRef; }

    public String getGrossDescription() { return grossDescription; }
    public void setGrossDescription(String grossDescription) { this.grossDescription = grossDescription; }

    public String getMicroscopicDescription() { return microscopicDescription; }
    public void setMicroscopicDescription(String microscopicDescription) { this.microscopicDescription = microscopicDescription; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getAncillaryTests() { return ancillaryTests; }
    public void setAncillaryTests(String ancillaryTests) { this.ancillaryTests = ancillaryTests; }

    public String getReportingPathologist() { return reportingPathologist; }
    public void setReportingPathologist(String reportingPathologist) { this.reportingPathologist = reportingPathologist; }

    public String getSignOutStatus() { return signOutStatus; }
    public void setSignOutStatus(String signOutStatus) { this.signOutStatus = signOutStatus; }

    public OffsetDateTime getSignedOutAt() { return signedOutAt; }
    public void setSignedOutAt(OffsetDateTime signedOutAt) { this.signedOutAt = signedOutAt; }

    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }

    public String getCriticalReason() { return criticalReason; }
    public void setCriticalReason(String criticalReason) { this.criticalReason = criticalReason; }

    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public String getButanoReportRef() { return butanoReportRef; }
    public void setButanoReportRef(String butanoReportRef) { this.butanoReportRef = butanoReportRef; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
