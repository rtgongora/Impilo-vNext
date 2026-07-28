package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A versioned entry in the canonical procedure catalogue.
 *
 * <p>Identity is {@code (tenant, definition_code, version)} and a published version is
 * immutable: some completed procedure was judged against it, so amending it in place would
 * rewrite the standard a past procedure was held to. Amendment creates a new version.</p>
 *
 * <p>JSONB columns carry {@code @JdbcTypeCode(SqlTypes.JSON)}. Without it Hibernate binds
 * them as varchar and Postgres rejects the write with SQLSTATE 42804 — a failure H2 masks
 * entirely, which is how the same defect reached production from the trauma stream.</p>
 */
@Entity
@Table(name = "procedure_definition", schema = "procedures")
public class ProcedureDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "definition_code", nullable = false, length = 64)
    private String definitionCode;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "clinical_name", nullable = false)
    private String clinicalName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<String> synonyms = new ArrayList<>();

    @Column(nullable = false, length = 48)
    private String category;

    @Column(name = "owning_specialty", nullable = false, length = 64)
    private String owningSpecialty;

    @Column(nullable = false, length = 24)
    private String purpose;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permitted_settings")
    private List<String> permittedSettings = new ArrayList<>();

    @Column(name = "anatomical_site", length = 128)
    private String anatomicalSite;

    @Column(name = "laterality_applicability", nullable = false, length = 24)
    private String lateralityApplicability;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technique_variants")
    private List<String> techniqueVariants = new ArrayList<>();

    @Column(name = "age_min_days")
    private Integer ageMinDays;

    @Column(name = "age_max_days")
    private Integer ageMaxDays;

    @Column(name = "pregnancy_applicability", nullable = false, length = 24)
    private String pregnancyApplicability;

    @Column(name = "indication_rule_ref", length = 128)
    private String indicationRuleRef;

    @Column(name = "contraindication_rule_ref", length = 128)
    private String contraindicationRuleRef;

    @Column(name = "expected_duration_min")
    private Integer expectedDurationMin;

    @Column(name = "recovery_required", nullable = false)
    private boolean recoveryRequired;

    @Column(name = "observation_minutes")
    private Integer observationMinutes;

    @Column(name = "consent_type", length = 48)
    private String consentType;

    @Column(name = "safety_pause_template", length = 64)
    private String safetyPauseTemplate;

    @Column(name = "findings_template", length = 64)
    private String findingsTemplate;

    @Column(name = "aftercare_template", length = 64)
    private String aftercareTemplate;

    // ── P7/P9 linkage columns. Resolve the SPECIFIC codes above (safetyPauseTemplate,
    // aftercareTemplate) first — they name a real procedure class, not a coarse setting. These
    // three are the coarser setting-class fallbacks P7/P9 seeded; defaultAftercareTemplateCode
    // in particular exists only because aftercareTemplate's own specific taxonomy (34 distinct
    // V003 codes) is far from fully resolved — see V007's header for the finding and the fix. ──
    @Column(name = "default_sedation_level_code", length = 32)
    private String defaultSedationLevelCode;

    @Column(name = "default_recovery_setting_code", length = 32)
    private String defaultRecoverySettingCode;

    @Column(name = "default_aftercare_template_code", length = 64)
    private String defaultAftercareTemplateCode;

    @Column(name = "complication_profile", length = 64)
    private String complicationProfile;

    @Column(name = "follow_up_policy", length = 64)
    private String followUpPolicy;

    @Column(name = "zibo_code", length = 64)
    private String ziboCode;

    @Column(name = "snomed_ct_code", length = 32)
    private String snomedCtCode;

    @Column(name = "icd9cm_procedure_code", length = 16)
    private String icd9cmProcedureCode;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "approving_authority", length = 128)
    private String approvingAuthority;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "source_citation")
    private String sourceCitation;

    /** Null means no window declared — reported as UNKNOWN, never treated as zero. */
    @Column(name = "duplicate_lookback_days")
    private Integer duplicateLookbackDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conflicts_with")
    private List<String> conflictsWith = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prerequisite_codes")
    private List<String> prerequisiteCodes = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id")
    @OrderBy("displayOrder ASC")
    private List<ProcedureRequirementEntity> requirements = new ArrayList<>();

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getDefinitionCode() { return definitionCode; }
    public Integer getVersion() { return version; }
    public String getClinicalName() { return clinicalName; }
    public List<String> getSynonyms() { return synonyms; }
    public String getCategory() { return category; }
    public String getOwningSpecialty() { return owningSpecialty; }
    public String getPurpose() { return purpose; }
    public List<String> getPermittedSettings() { return permittedSettings; }
    public String getAnatomicalSite() { return anatomicalSite; }
    public String getLateralityApplicability() { return lateralityApplicability; }
    public List<String> getTechniqueVariants() { return techniqueVariants; }
    public Integer getAgeMinDays() { return ageMinDays; }
    public Integer getAgeMaxDays() { return ageMaxDays; }
    public String getPregnancyApplicability() { return pregnancyApplicability; }
    public String getIndicationRuleRef() { return indicationRuleRef; }
    public String getContraindicationRuleRef() { return contraindicationRuleRef; }
    public Integer getExpectedDurationMin() { return expectedDurationMin; }
    public boolean isRecoveryRequired() { return recoveryRequired; }
    public Integer getObservationMinutes() { return observationMinutes; }
    public String getConsentType() { return consentType; }
    public String getSafetyPauseTemplate() { return safetyPauseTemplate; }
    public String getFindingsTemplate() { return findingsTemplate; }
    public String getAftercareTemplate() { return aftercareTemplate; }
    public String getDefaultSedationLevelCode() { return defaultSedationLevelCode; }
    public String getDefaultRecoverySettingCode() { return defaultRecoverySettingCode; }
    public String getDefaultAftercareTemplateCode() { return defaultAftercareTemplateCode; }
    public String getComplicationProfile() { return complicationProfile; }
    public String getFollowUpPolicy() { return followUpPolicy; }
    public String getZiboCode() { return ziboCode; }
    public String getSnomedCtCode() { return snomedCtCode; }
    public String getIcd9cmProcedureCode() { return icd9cmProcedureCode; }
    public String getStatus() { return status; }
    public String getApprovingAuthority() { return approvingAuthority; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public String getSourceCitation() { return sourceCitation; }
    public Integer getDuplicateLookbackDays() { return duplicateLookbackDays; }
    public List<String> getConflictsWith() { return conflictsWith; }
    public List<String> getPrerequisiteCodes() { return prerequisiteCodes; }
    public List<ProcedureRequirementEntity> getRequirements() { return requirements; }
}
