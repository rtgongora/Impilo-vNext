package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single current regulatory classification profile for a facility — binds
 * an imported national facility to the HPA classification catalogue
 * (V018 {@code tuso.facility_classification}) without mutating the imported
 * source truth. One row per facility; every change is appended to
 * {@link FacilityRegulatoryProfileHistoryEntity}.
 */
@Entity
@Table(name = "facility_regulatory_profile", schema = "tuso")
public class FacilityRegulatoryProfileEntity {

    @Id
    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    // Verbatim source snapshot — never rewritten by review.
    @Column(name = "source_facility_type", length = 128)
    private String sourceFacilityType;

    @Column(name = "source_ownership", length = 128)
    private String sourceOwnership;

    @Column(name = "source_level", length = 64)
    private String sourceLevel;

    @Column(name = "source_bed_capacity", length = 64)
    private String sourceBedCapacity;

    // Canonical dimensions — conservative, UNKNOWN until proven.
    @Column(name = "canonical_type", length = 64)
    private String canonicalType;

    @Column(name = "ownership_authority", length = 64)
    private String ownershipAuthority;

    @Column(name = "hpa_route", length = 64)
    private String hpaRoute;

    @Column(name = "care_setting", nullable = false, length = 32)
    private String careSetting = "UNKNOWN";

    @Column(name = "mobility", nullable = false, length = 32)
    private String mobility = "UNKNOWN";

    @Column(name = "specialist_scope", nullable = false, length = 32)
    private String specialistScope = "UNKNOWN";

    @Column(name = "bed_count_claimed")
    private Integer bedCountClaimed;

    @Column(name = "bed_count_confirmed")
    private Integer bedCountConfirmed;

    @Column(name = "bed_band", length = 32)
    private String bedBand;

    @Column(name = "bed_band_basis", length = 32)
    private String bedBandBasis;

    @Column(name = "hpa_class", nullable = false, length = 32)
    private String hpaClass = "NOT_YET_DETERMINED";

    @Column(name = "hpa_class_justification", columnDefinition = "text")
    private String hpaClassJustification;

    @Column(name = "hpa_classification_id")
    private UUID hpaClassificationId;

    @Column(name = "hpa_classification_label", length = 255)
    private String hpaClassificationLabel;

    @Column(name = "classification_status", nullable = false, length = 48)
    private String classificationStatus = "MISSING_REQUIRED_FACTS";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimension_statuses", columnDefinition = "jsonb")
    private Map<String, Object> dimensionStatuses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestion", columnDefinition = "jsonb")
    private List<Map<String, Object>> suggestion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_facts", columnDefinition = "jsonb")
    private List<String> requiredFacts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_fields_used", columnDefinition = "jsonb")
    private Map<String, Object> sourceFieldsUsed;

    @Column(name = "mapping_rule_code", length = 96)
    private String mappingRuleCode;

    @Column(name = "mapping_rule_version")
    private Integer mappingRuleVersion;

    @Column(name = "evaluation_fingerprint", length = 128)
    private String evaluationFingerprint;

    @Column(name = "units_review_status", nullable = false, length = 32)
    private String unitsReviewStatus = "NOT_REQUIRED";

    @Column(name = "confirmed_by", length = 255)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "reclassification_review_required", nullable = false)
    private boolean reclassificationReviewRequired = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (profileId == null) profileId = UUID.randomUUID();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getProfileId() { return profileId; }
    public void setProfileId(UUID profileId) { this.profileId = profileId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getSourceFacilityType() { return sourceFacilityType; }
    public void setSourceFacilityType(String sourceFacilityType) { this.sourceFacilityType = sourceFacilityType; }
    public String getSourceOwnership() { return sourceOwnership; }
    public void setSourceOwnership(String sourceOwnership) { this.sourceOwnership = sourceOwnership; }
    public String getSourceLevel() { return sourceLevel; }
    public void setSourceLevel(String sourceLevel) { this.sourceLevel = sourceLevel; }
    public String getSourceBedCapacity() { return sourceBedCapacity; }
    public void setSourceBedCapacity(String sourceBedCapacity) { this.sourceBedCapacity = sourceBedCapacity; }
    public String getCanonicalType() { return canonicalType; }
    public void setCanonicalType(String canonicalType) { this.canonicalType = canonicalType; }
    public String getOwnershipAuthority() { return ownershipAuthority; }
    public void setOwnershipAuthority(String ownershipAuthority) { this.ownershipAuthority = ownershipAuthority; }
    public String getHpaRoute() { return hpaRoute; }
    public void setHpaRoute(String hpaRoute) { this.hpaRoute = hpaRoute; }
    public String getCareSetting() { return careSetting; }
    public void setCareSetting(String careSetting) { this.careSetting = careSetting; }
    public String getMobility() { return mobility; }
    public void setMobility(String mobility) { this.mobility = mobility; }
    public String getSpecialistScope() { return specialistScope; }
    public void setSpecialistScope(String specialistScope) { this.specialistScope = specialistScope; }
    public Integer getBedCountClaimed() { return bedCountClaimed; }
    public void setBedCountClaimed(Integer bedCountClaimed) { this.bedCountClaimed = bedCountClaimed; }
    public Integer getBedCountConfirmed() { return bedCountConfirmed; }
    public void setBedCountConfirmed(Integer bedCountConfirmed) { this.bedCountConfirmed = bedCountConfirmed; }
    public String getBedBand() { return bedBand; }
    public void setBedBand(String bedBand) { this.bedBand = bedBand; }
    public String getBedBandBasis() { return bedBandBasis; }
    public void setBedBandBasis(String bedBandBasis) { this.bedBandBasis = bedBandBasis; }
    public String getHpaClass() { return hpaClass; }
    public void setHpaClass(String hpaClass) { this.hpaClass = hpaClass; }
    public String getHpaClassJustification() { return hpaClassJustification; }
    public void setHpaClassJustification(String hpaClassJustification) { this.hpaClassJustification = hpaClassJustification; }
    public UUID getHpaClassificationId() { return hpaClassificationId; }
    public void setHpaClassificationId(UUID hpaClassificationId) { this.hpaClassificationId = hpaClassificationId; }
    public String getHpaClassificationLabel() { return hpaClassificationLabel; }
    public void setHpaClassificationLabel(String hpaClassificationLabel) { this.hpaClassificationLabel = hpaClassificationLabel; }
    public String getClassificationStatus() { return classificationStatus; }
    public void setClassificationStatus(String classificationStatus) { this.classificationStatus = classificationStatus; }
    public Map<String, Object> getDimensionStatuses() { return dimensionStatuses; }
    public void setDimensionStatuses(Map<String, Object> dimensionStatuses) { this.dimensionStatuses = dimensionStatuses; }
    public List<Map<String, Object>> getSuggestion() { return suggestion; }
    public void setSuggestion(List<Map<String, Object>> suggestion) { this.suggestion = suggestion; }
    public List<String> getRequiredFacts() { return requiredFacts; }
    public void setRequiredFacts(List<String> requiredFacts) { this.requiredFacts = requiredFacts; }
    public Map<String, Object> getSourceFieldsUsed() { return sourceFieldsUsed; }
    public void setSourceFieldsUsed(Map<String, Object> sourceFieldsUsed) { this.sourceFieldsUsed = sourceFieldsUsed; }
    public String getMappingRuleCode() { return mappingRuleCode; }
    public void setMappingRuleCode(String mappingRuleCode) { this.mappingRuleCode = mappingRuleCode; }
    public Integer getMappingRuleVersion() { return mappingRuleVersion; }
    public void setMappingRuleVersion(Integer mappingRuleVersion) { this.mappingRuleVersion = mappingRuleVersion; }
    public String getEvaluationFingerprint() { return evaluationFingerprint; }
    public void setEvaluationFingerprint(String evaluationFingerprint) { this.evaluationFingerprint = evaluationFingerprint; }
    public String getUnitsReviewStatus() { return unitsReviewStatus; }
    public void setUnitsReviewStatus(String unitsReviewStatus) { this.unitsReviewStatus = unitsReviewStatus; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public boolean isReclassificationReviewRequired() { return reclassificationReviewRequired; }
    public void setReclassificationReviewRequired(boolean reclassificationReviewRequired) { this.reclassificationReviewRequired = reclassificationReviewRequired; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
