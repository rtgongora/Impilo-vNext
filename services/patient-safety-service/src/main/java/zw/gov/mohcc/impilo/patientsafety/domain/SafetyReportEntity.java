package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Safety report header — a captured ADR or AEFI intake. Lifecycle:
 * DRAFT → SUBMITTED → NEEDS_MORE_INFORMATION → UNDER_REVIEW → ESCALATED → DUPLICATE/INVALID/CLOSED.
 */
@Entity
@Table(name = "ps_safety_report")
public class SafetyReportEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "reference_code", length = 32)
    private String referenceCode;

    @Column(name = "report_type", nullable = false, length = 16)
    private String reportType;          // ADR | AEFI

    @Column(name = "reporter_kind", nullable = false, length = 16)
    private String reporterKind;        // PROVIDER | CITIZEN | CAREGIVER

    @Column(name = "source_context", nullable = false, length = 32)
    private String sourceContext = "STANDALONE";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "reporter_actor_id", length = 255)
    private String reporterActorId;
    @Column(name = "reporter_actor_type", length = 64)
    private String reporterActorType;
    @Column(name = "reporter_name", length = 255)
    private String reporterName;
    @Column(name = "reporter_role", length = 128)
    private String reporterRole;
    @Column(name = "reporter_facility_id", length = 255)
    private String reporterFacilityId;
    @Column(name = "reporter_phone", length = 64)
    private String reporterPhone;
    @Column(name = "reporter_email", length = 255)
    private String reporterEmail;

    @Column(name = "subject_cpid", length = 255)
    private String subjectCpid;
    @Column(name = "subject_is_self", nullable = false)
    private boolean subjectIsSelf = false;
    @Column(name = "subject_initials", length = 16)
    private String subjectInitials;
    @Column(name = "subject_sex", length = 16)
    private String subjectSex;
    @Column(name = "subject_dob")
    private LocalDate subjectDob;
    @Column(name = "subject_age_value")
    private Integer subjectAgeValue;
    @Column(name = "subject_age_unit", length = 16)
    private String subjectAgeUnit;
    @Column(name = "subject_weight_kg")
    private BigDecimal subjectWeightKg;

    @Column(name = "encounter_id", length = 255)
    private String encounterId;

    @Column(name = "narrative", columnDefinition = "TEXT")
    private String narrative;
    @Column(name = "onset_date")
    private LocalDate onsetDate;
    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "is_serious", nullable = false)
    private boolean serious = false;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seriousness_reasons", columnDefinition = "jsonb")
    private String seriousnessReasons;  // JSON array string
    @Column(name = "seriousness_other_text", columnDefinition = "TEXT")
    private String seriousnessOtherText;

    @Column(name = "form_pack_key", length = 128)
    private String formPackKey;
    @Column(name = "form_pack_version", length = 32)
    private String formPackVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", columnDefinition = "jsonb")
    private String formData;

    @Column(name = "created_by", length = 255)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    protected SafetyReportEntity() {}

    public SafetyReportEntity(UUID tenantId, String podId, String reportType, String reporterKind) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        if (podId != null && !podId.isBlank()) this.podId = podId;
        this.reportType = reportType;
        this.reporterKind = reporterKind;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getReporterKind() { return reporterKind; }
    public void setReporterKind(String reporterKind) { this.reporterKind = reporterKind; }
    public String getSourceContext() { return sourceContext; }
    public void setSourceContext(String sourceContext) { this.sourceContext = sourceContext; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReporterActorId() { return reporterActorId; }
    public void setReporterActorId(String v) { this.reporterActorId = v; }
    public String getReporterActorType() { return reporterActorType; }
    public void setReporterActorType(String v) { this.reporterActorType = v; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String v) { this.reporterName = v; }
    public String getReporterRole() { return reporterRole; }
    public void setReporterRole(String v) { this.reporterRole = v; }
    public String getReporterFacilityId() { return reporterFacilityId; }
    public void setReporterFacilityId(String v) { this.reporterFacilityId = v; }
    public String getReporterPhone() { return reporterPhone; }
    public void setReporterPhone(String v) { this.reporterPhone = v; }
    public String getReporterEmail() { return reporterEmail; }
    public void setReporterEmail(String v) { this.reporterEmail = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public boolean isSubjectIsSelf() { return subjectIsSelf; }
    public void setSubjectIsSelf(boolean v) { this.subjectIsSelf = v; }
    public String getSubjectInitials() { return subjectInitials; }
    public void setSubjectInitials(String v) { this.subjectInitials = v; }
    public String getSubjectSex() { return subjectSex; }
    public void setSubjectSex(String v) { this.subjectSex = v; }
    public LocalDate getSubjectDob() { return subjectDob; }
    public void setSubjectDob(LocalDate v) { this.subjectDob = v; }
    public Integer getSubjectAgeValue() { return subjectAgeValue; }
    public void setSubjectAgeValue(Integer v) { this.subjectAgeValue = v; }
    public String getSubjectAgeUnit() { return subjectAgeUnit; }
    public void setSubjectAgeUnit(String v) { this.subjectAgeUnit = v; }
    public BigDecimal getSubjectWeightKg() { return subjectWeightKg; }
    public void setSubjectWeightKg(BigDecimal v) { this.subjectWeightKg = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { this.encounterId = v; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String v) { this.narrative = v; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate v) { this.onsetDate = v; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate v) { this.reportDate = v; }
    public boolean isSerious() { return serious; }
    public void setSerious(boolean v) { this.serious = v; }
    public String getSeriousnessReasons() { return seriousnessReasons; }
    public void setSeriousnessReasons(String v) { this.seriousnessReasons = v; }
    public String getSeriousnessOtherText() { return seriousnessOtherText; }
    public void setSeriousnessOtherText(String v) { this.seriousnessOtherText = v; }
    public String getFormPackKey() { return formPackKey; }
    public void setFormPackKey(String v) { this.formPackKey = v; }
    public String getFormPackVersion() { return formPackVersion; }
    public void setFormPackVersion(String v) { this.formPackVersion = v; }
    public String getFormData() { return formData; }
    public void setFormData(String v) { this.formData = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
}
