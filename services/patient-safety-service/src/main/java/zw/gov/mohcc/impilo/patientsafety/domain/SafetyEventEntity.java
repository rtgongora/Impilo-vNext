package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An adverse reaction / event captured on a safety report. */
@Entity
@Table(name = "ps_safety_event")
public class SafetyEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "report_id", nullable = false)
    private UUID reportId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "term", nullable = false, length = 512)
    private String term;
    @Column(name = "meddra_code", length = 32)
    private String meddraCode;
    @Column(name = "meddra_pt", length = 255)
    private String meddraPt;
    @Column(name = "onset_date")
    private LocalDate onsetDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(name = "severity", length = 16)
    private String severity;        // MILD | MODERATE | SEVERE
    @Column(name = "outcome", length = 32)
    private String outcome;         // RECOVERED | RECOVERING | NOT_RECOVERED | RECOVERED_WITH_SEQUELAE | FATAL | UNKNOWN
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SafetyEventEntity() {}

    public SafetyEventEntity(UUID reportId, UUID tenantId, String term) {
        this.id = UUID.randomUUID();
        this.reportId = reportId;
        this.tenantId = tenantId;
        this.term = term;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getReportId() { return reportId; }
    public UUID getTenantId() { return tenantId; }
    public String getTerm() { return term; }
    public void setTerm(String v) { this.term = v; }
    public String getMeddraCode() { return meddraCode; }
    public void setMeddraCode(String v) { this.meddraCode = v; }
    public String getMeddraPt() { return meddraPt; }
    public void setMeddraPt(String v) { this.meddraPt = v; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate v) { this.onsetDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
