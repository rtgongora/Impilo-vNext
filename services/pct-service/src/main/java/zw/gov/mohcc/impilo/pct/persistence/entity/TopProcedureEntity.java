package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A termination procedure. Its {@code authorisationStatus} is fixed to AUTHORISED and composite-FK'd
 * to the authorisation, so the row cannot exist without an authorised authorisation and the
 * authorisation cannot leave that state while it does (V435). Aggregate-only: there is no event path.
 */
@Entity
@Table(name = "pct_top_procedures")
public class TopProcedureEntity {

    @Id @Column(name = "procedure_id") private UUID procedureId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "authorisation_id") private UUID authorisationId;
    @Column(name = "authorisation_status") private String authorisationStatus;
    @Column(name = "method") private String method;
    @Column(name = "gestation_weeks") private BigDecimal gestationWeeks;
    @Column(name = "performed_on") private LocalDate performedOn;
    @Column(name = "performed_by") private String performedBy;
    @Column(name = "complications") private String complications;
    @Column(name = "followup_contraception") private String followupContraception;
    @Column(name = "followup_plan") private String followupPlan;
    @Column(name = "sensitivity_class") private String sensitivityClass;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "recorded_at") private OffsetDateTime recordedAt;

    public UUID getProcedureId() { return procedureId; }
    public void setProcedureId(UUID v) { this.procedureId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getAuthorisationId() { return authorisationId; }
    public void setAuthorisationId(UUID v) { this.authorisationId = v; }
    public String getAuthorisationStatus() { return authorisationStatus; }
    public void setAuthorisationStatus(String v) { this.authorisationStatus = v; }
    public String getMethod() { return method; }
    public void setMethod(String v) { this.method = v; }
    public BigDecimal getGestationWeeks() { return gestationWeeks; }
    public void setGestationWeeks(BigDecimal v) { this.gestationWeeks = v; }
    public LocalDate getPerformedOn() { return performedOn; }
    public void setPerformedOn(LocalDate v) { this.performedOn = v; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String v) { this.performedBy = v; }
    public String getComplications() { return complications; }
    public void setComplications(String v) { this.complications = v; }
    public String getFollowupContraception() { return followupContraception; }
    public void setFollowupContraception(String v) { this.followupContraception = v; }
    public String getFollowupPlan() { return followupPlan; }
    public void setFollowupPlan(String v) { this.followupPlan = v; }
    public String getSensitivityClass() { return sensitivityClass; }
    public void setSensitivityClass(String v) { this.sensitivityClass = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }

    // Confidentiality stamp (V437). Three values travel with the class above: the governed CATEGORY a
    // grant is matched against, WHY the stamp was applied, and WHICH policy version decided it.
    @Column(name = "confidentiality_category") private String confidentialityCategory;
    @Column(name = "confidentiality_basis") private String confidentialityBasis;
    @Column(name = "confidentiality_policy_version") private String confidentialityPolicyVersion;

    public String getConfidentialityCategory() { return confidentialityCategory; }
    public void setConfidentialityCategory(String v) { this.confidentialityCategory = v; }
    public String getConfidentialityBasis() { return confidentialityBasis; }
    public void setConfidentialityBasis(String v) { this.confidentialityBasis = v; }
    public String getConfidentialityPolicyVersion() { return confidentialityPolicyVersion; }
    public void setConfidentialityPolicyVersion(String v) { this.confidentialityPolicyVersion = v; }

}
