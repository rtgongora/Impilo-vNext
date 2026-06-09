package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ed_disposition")
public class EdDispositionEntity {

    @Id
    @Column(name = "disposition_id")
    private UUID dispositionId;

    @Column(name = "visit_id", nullable = false, unique = true)
    private UUID visitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "disposition_type", nullable = false)
    private String dispositionType;

    @Column(name = "primary_diagnosis_code")
    private String primaryDiagnosisCode;

    @Column(name = "primary_diagnosis_display")
    private String primaryDiagnosisDisplay;

    @Column(name = "secondary_diagnoses", columnDefinition = "jsonb")
    private String secondaryDiagnosesJson = "[]";

    @Column(name = "external_cause_code")
    private String externalCauseCode;

    @Column(name = "external_cause_display")
    private String externalCauseDisplay;

    @Column(name = "procedure_codes", columnDefinition = "jsonb")
    private String procedureCodesJson = "[]";

    @Column(name = "discharge_summary", columnDefinition = "TEXT")
    private String dischargeSummary;

    @Column(name = "destination_facility")
    private String destinationFacility;

    @Column(name = "destination_ward")
    private String destinationWard;

    @Column(name = "disposition_by", nullable = false)
    private String dispositionBy;

    @Column(name = "disposition_at", nullable = false)
    private OffsetDateTime dispositionAt;

    @PrePersist
    protected void onCreate() {
        if (dispositionId == null) dispositionId = UUID.randomUUID();
        if (dispositionAt == null) dispositionAt = OffsetDateTime.now();
    }

    public UUID getDispositionId() { return dispositionId; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getDispositionType() { return dispositionType; }
    public void setDispositionType(String dispositionType) { this.dispositionType = dispositionType; }
    public String getPrimaryDiagnosisCode() { return primaryDiagnosisCode; }
    public void setPrimaryDiagnosisCode(String primaryDiagnosisCode) { this.primaryDiagnosisCode = primaryDiagnosisCode; }
    public String getPrimaryDiagnosisDisplay() { return primaryDiagnosisDisplay; }
    public void setPrimaryDiagnosisDisplay(String primaryDiagnosisDisplay) { this.primaryDiagnosisDisplay = primaryDiagnosisDisplay; }
    public String getSecondaryDiagnosesJson() { return secondaryDiagnosesJson; }
    public void setSecondaryDiagnosesJson(String secondaryDiagnosesJson) { this.secondaryDiagnosesJson = secondaryDiagnosesJson; }
    public String getExternalCauseCode() { return externalCauseCode; }
    public void setExternalCauseCode(String externalCauseCode) { this.externalCauseCode = externalCauseCode; }
    public String getExternalCauseDisplay() { return externalCauseDisplay; }
    public void setExternalCauseDisplay(String externalCauseDisplay) { this.externalCauseDisplay = externalCauseDisplay; }
    public String getProcedureCodesJson() { return procedureCodesJson; }
    public void setProcedureCodesJson(String procedureCodesJson) { this.procedureCodesJson = procedureCodesJson; }
    public String getDischargeSummary() { return dischargeSummary; }
    public void setDischargeSummary(String dischargeSummary) { this.dischargeSummary = dischargeSummary; }
    public String getDestinationFacility() { return destinationFacility; }
    public void setDestinationFacility(String destinationFacility) { this.destinationFacility = destinationFacility; }
    public String getDestinationWard() { return destinationWard; }
    public void setDestinationWard(String destinationWard) { this.destinationWard = destinationWard; }
    public String getDispositionBy() { return dispositionBy; }
    public void setDispositionBy(String dispositionBy) { this.dispositionBy = dispositionBy; }
    public OffsetDateTime getDispositionAt() { return dispositionAt; }
}
