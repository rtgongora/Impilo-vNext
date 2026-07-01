package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Row-level outcome of a single record evaluated in a {@link FacilityImportRunEntity}. Persists both
 * the raw source values ({@code rawValues} JSONB — never lost during canonicalisation) and the canonical
 * (imported) values, the evaluated outcome/decision, exclusion + duplicate-review metadata, acceptable-
 * missing flags, and matched/result facility references. Backs the admin row/review views.
 */
@Entity
@Table(name = "facility_import_row", schema = "tuso")
public class FacilityImportRowEntity {

    /** Canonical row-outcome vocabulary. */
    public static final String READY_FOR_IMPORT = "READY_FOR_IMPORT";
    public static final String IMPORTED = "IMPORTED";
    public static final String EXCLUDED_MISSING_FACILITY_CODE = "EXCLUDED_MISSING_FACILITY_CODE";
    public static final String EXCLUDED_DUPLICATE_FACILITY_CODE = "EXCLUDED_DUPLICATE_FACILITY_CODE";
    public static final String EXCLUDED_DUPLICATE_FACILITY_NAME = "EXCLUDED_DUPLICATE_FACILITY_NAME";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";
    public static final String REQUIRES_REVIEW = "REQUIRES_REVIEW";

    public static final String DUP_TYPE_CODE = "FACILITY_CODE";
    public static final String DUP_TYPE_NAME = "FACILITY_NAME";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_run_id", nullable = false)
    private Long importRunId;

    @Column(name = "source_label", length = 128)
    private String sourceLabel;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_row", length = 32)
    private String sourceRow;

    @Column(name = "correlation_key", length = 255)
    private String correlationKey;

    @Column(name = "province", length = 128)
    private String province;

    @Column(name = "district", length = 128)
    private String district;

    @Column(name = "facility_name", length = 255)
    private String facilityName;

    @Column(name = "facility_code", length = 64)
    private String facilityCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "facility_type", length = 64)
    private String facilityType;

    @Column(name = "ownership", length = 64)
    private String ownership;

    @Column(name = "location_category", length = 64)
    private String locationCategory;

    @Column(name = "facility_level", length = 64)
    private String facilityLevel;

    @Column(name = "contact", length = 128)
    private String contact;

    @Column(name = "operating_status", length = 64)
    private String operatingStatus;

    @Column(name = "bed_capacity")
    private Integer bedCapacity;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_values", columnDefinition = "jsonb")
    private Map<String, Object> rawValues;

    @Column(name = "outcome", nullable = false, length = 48)
    private String outcome;

    @Column(name = "import_decision", length = 24)
    private String importDecision;

    @Column(name = "exclusion_reason", length = 128)
    private String exclusionReason;

    @Column(name = "duplicate_group_key", length = 255)
    private String duplicateGroupKey;

    @Column(name = "duplicate_type", length = 24)
    private String duplicateType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acceptable_missing", columnDefinition = "jsonb")
    private Map<String, Object> acceptableMissing;

    @Column(name = "has_acceptable_missing", nullable = false)
    private boolean hasAcceptableMissing;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_warnings", columnDefinition = "jsonb")
    private java.util.List<String> validationWarnings;

    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;

    @Column(name = "matched_facility_id")
    private Long matchedFacilityId;

    @Column(name = "result_facility_id")
    private Long resultFacilityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getImportRunId() { return importRunId; }
    public void setImportRunId(Long importRunId) { this.importRunId = importRunId; }

    public String getSourceLabel() { return sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }

    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

    public String getSourceRow() { return sourceRow; }
    public void setSourceRow(String sourceRow) { this.sourceRow = sourceRow; }

    public String getCorrelationKey() { return correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }

    public String getFacilityCode() { return facilityCode; }
    public void setFacilityCode(String facilityCode) { this.facilityCode = facilityCode; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }

    public String getOwnership() { return ownership; }
    public void setOwnership(String ownership) { this.ownership = ownership; }

    public String getLocationCategory() { return locationCategory; }
    public void setLocationCategory(String locationCategory) { this.locationCategory = locationCategory; }

    public String getFacilityLevel() { return facilityLevel; }
    public void setFacilityLevel(String facilityLevel) { this.facilityLevel = facilityLevel; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getOperatingStatus() { return operatingStatus; }
    public void setOperatingStatus(String operatingStatus) { this.operatingStatus = operatingStatus; }

    public Integer getBedCapacity() { return bedCapacity; }
    public void setBedCapacity(Integer bedCapacity) { this.bedCapacity = bedCapacity; }

    public LocalDate getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDate openingDate) { this.openingDate = openingDate; }

    public LocalDate getClosureDate() { return closureDate; }
    public void setClosureDate(LocalDate closureDate) { this.closureDate = closureDate; }

    public Map<String, Object> getRawValues() { return rawValues; }
    public void setRawValues(Map<String, Object> rawValues) { this.rawValues = rawValues; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getImportDecision() { return importDecision; }
    public void setImportDecision(String importDecision) { this.importDecision = importDecision; }

    public String getExclusionReason() { return exclusionReason; }
    public void setExclusionReason(String exclusionReason) { this.exclusionReason = exclusionReason; }

    public String getDuplicateGroupKey() { return duplicateGroupKey; }
    public void setDuplicateGroupKey(String duplicateGroupKey) { this.duplicateGroupKey = duplicateGroupKey; }

    public String getDuplicateType() { return duplicateType; }
    public void setDuplicateType(String duplicateType) { this.duplicateType = duplicateType; }

    public Map<String, Object> getAcceptableMissing() { return acceptableMissing; }
    public void setAcceptableMissing(Map<String, Object> acceptableMissing) { this.acceptableMissing = acceptableMissing; }

    public boolean isHasAcceptableMissing() { return hasAcceptableMissing; }
    public void setHasAcceptableMissing(boolean hasAcceptableMissing) { this.hasAcceptableMissing = hasAcceptableMissing; }

    public java.util.List<String> getValidationWarnings() { return validationWarnings; }
    public void setValidationWarnings(java.util.List<String> validationWarnings) { this.validationWarnings = validationWarnings; }

    public String getValidationErrors() { return validationErrors; }
    public void setValidationErrors(String validationErrors) { this.validationErrors = validationErrors; }

    public Long getMatchedFacilityId() { return matchedFacilityId; }
    public void setMatchedFacilityId(Long matchedFacilityId) { this.matchedFacilityId = matchedFacilityId; }

    public Long getResultFacilityId() { return resultFacilityId; }
    public void setResultFacilityId(Long resultFacilityId) { this.resultFacilityId = resultFacilityId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
