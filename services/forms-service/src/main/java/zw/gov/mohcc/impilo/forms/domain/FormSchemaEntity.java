package zw.gov.mohcc.impilo.forms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fs_form_schemas")
public class FormSchemaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "form_key", length = 128, nullable = false)
    private String formKey;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "DRAFT";

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pod_id", length = 64, nullable = false)
    private String podId;

    // --- Clinical encounter-form metadata (V002); nullable/defaulted for backward compat ---

    @Column(name = "form_type", length = 64)
    private String formType;

    @Column(name = "care_settings", columnDefinition = "TEXT")
    private String careSettings;              // JSON array

    @Column(name = "care_stages", columnDefinition = "TEXT")
    private String careStages;                // JSON array

    @Column(name = "specialties", columnDefinition = "TEXT")
    private String specialties;               // JSON array

    @Column(name = "encounter_types", columnDefinition = "TEXT")
    private String encounterTypes;            // JSON array

    @Column(name = "encounter_contexts", columnDefinition = "TEXT")
    private String encounterContexts;         // JSON array

    @Column(name = "required_workflow", length = 48)
    private String requiredWorkflow;

    @Column(name = "obligation_default", length = 24, nullable = false)
    private String obligationDefault = "OPTIONAL";

    @Column(name = "permitted_cadres", columnDefinition = "TEXT")
    private String permittedCadres;           // JSON array

    @Column(name = "min_age_months")
    private Integer minAgeMonths;

    @Column(name = "max_age_months")
    private Integer maxAgeMonths;

    @Column(name = "sex_applicability", length = 16)
    private String sexApplicability = "ALL";

    @Column(name = "require_pregnant")
    private Boolean requirePregnant;

    @Column(name = "programme_applicability", columnDefinition = "TEXT")
    private String programmeApplicability;    // JSON array

    @Column(name = "facility_levels", columnDefinition = "TEXT")
    private String facilityLevels;            // JSON array

    @Column(name = "eligibility_json", columnDefinition = "TEXT")
    private String eligibilityJson;           // JSON

    @Column(name = "terminology_bindings", columnDefinition = "TEXT")
    private String terminologyBindings;       // JSON

    @Column(name = "resource_mappings", columnDefinition = "TEXT")
    private String resourceMappings;          // JSON

    @Column(name = "indicators", columnDefinition = "TEXT")
    private String indicators;                // JSON

    @Column(name = "requires_countersign", nullable = false)
    private boolean requiresCountersign = false;

    @Column(name = "offline_capable", nullable = false)
    private boolean offlineCapable = false;

    @Column(name = "sensitivity", length = 24, nullable = false)
    private String sensitivity = "STANDARD";

    @Column(name = "governance_author", length = 255)
    private String governanceAuthor;

    @Column(name = "governance_reviewer", length = 255)
    private String governanceReviewer;

    @Column(name = "governance_approver", length = 255)
    private String governanceApprover;

    @Column(name = "effective_date")
    private java.time.LocalDate effectiveDate;

    @Column(name = "review_date")
    private java.time.LocalDate reviewDate;

    @Column(name = "source_guideline", length = 512)
    private String sourceGuideline;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "is_clinical", nullable = false)
    private boolean clinical = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    // --- Clinical metadata accessors ---

    public String getFormType() { return formType; }
    public void setFormType(String formType) { this.formType = formType; }

    public String getCareSettings() { return careSettings; }
    public void setCareSettings(String careSettings) { this.careSettings = careSettings; }

    public String getCareStages() { return careStages; }
    public void setCareStages(String careStages) { this.careStages = careStages; }

    public String getSpecialties() { return specialties; }
    public void setSpecialties(String specialties) { this.specialties = specialties; }

    public String getEncounterTypes() { return encounterTypes; }
    public void setEncounterTypes(String encounterTypes) { this.encounterTypes = encounterTypes; }

    public String getEncounterContexts() { return encounterContexts; }
    public void setEncounterContexts(String encounterContexts) { this.encounterContexts = encounterContexts; }

    public String getRequiredWorkflow() { return requiredWorkflow; }
    public void setRequiredWorkflow(String requiredWorkflow) { this.requiredWorkflow = requiredWorkflow; }

    public String getObligationDefault() { return obligationDefault; }
    public void setObligationDefault(String obligationDefault) { this.obligationDefault = obligationDefault; }

    public String getPermittedCadres() { return permittedCadres; }
    public void setPermittedCadres(String permittedCadres) { this.permittedCadres = permittedCadres; }

    public Integer getMinAgeMonths() { return minAgeMonths; }
    public void setMinAgeMonths(Integer minAgeMonths) { this.minAgeMonths = minAgeMonths; }

    public Integer getMaxAgeMonths() { return maxAgeMonths; }
    public void setMaxAgeMonths(Integer maxAgeMonths) { this.maxAgeMonths = maxAgeMonths; }

    public String getSexApplicability() { return sexApplicability; }
    public void setSexApplicability(String sexApplicability) { this.sexApplicability = sexApplicability; }

    public Boolean getRequirePregnant() { return requirePregnant; }
    public void setRequirePregnant(Boolean requirePregnant) { this.requirePregnant = requirePregnant; }

    public String getProgrammeApplicability() { return programmeApplicability; }
    public void setProgrammeApplicability(String programmeApplicability) { this.programmeApplicability = programmeApplicability; }

    public String getFacilityLevels() { return facilityLevels; }
    public void setFacilityLevels(String facilityLevels) { this.facilityLevels = facilityLevels; }

    public String getEligibilityJson() { return eligibilityJson; }
    public void setEligibilityJson(String eligibilityJson) { this.eligibilityJson = eligibilityJson; }

    public String getTerminologyBindings() { return terminologyBindings; }
    public void setTerminologyBindings(String terminologyBindings) { this.terminologyBindings = terminologyBindings; }

    public String getResourceMappings() { return resourceMappings; }
    public void setResourceMappings(String resourceMappings) { this.resourceMappings = resourceMappings; }

    public String getIndicators() { return indicators; }
    public void setIndicators(String indicators) { this.indicators = indicators; }

    public boolean isRequiresCountersign() { return requiresCountersign; }
    public void setRequiresCountersign(boolean requiresCountersign) { this.requiresCountersign = requiresCountersign; }

    public boolean isOfflineCapable() { return offlineCapable; }
    public void setOfflineCapable(boolean offlineCapable) { this.offlineCapable = offlineCapable; }

    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }

    public String getGovernanceAuthor() { return governanceAuthor; }
    public void setGovernanceAuthor(String governanceAuthor) { this.governanceAuthor = governanceAuthor; }

    public String getGovernanceReviewer() { return governanceReviewer; }
    public void setGovernanceReviewer(String governanceReviewer) { this.governanceReviewer = governanceReviewer; }

    public String getGovernanceApprover() { return governanceApprover; }
    public void setGovernanceApprover(String governanceApprover) { this.governanceApprover = governanceApprover; }

    public java.time.LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(java.time.LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public java.time.LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(java.time.LocalDate reviewDate) { this.reviewDate = reviewDate; }

    public String getSourceGuideline() { return sourceGuideline; }
    public void setSourceGuideline(String sourceGuideline) { this.sourceGuideline = sourceGuideline; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public boolean isClinical() { return clinical; }
    public void setClinical(boolean clinical) { this.clinical = clinical; }
}
