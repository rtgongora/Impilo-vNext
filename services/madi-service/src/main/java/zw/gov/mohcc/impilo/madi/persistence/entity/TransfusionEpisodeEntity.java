package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transfusion_episodes", schema = "madi")
public class TransfusionEpisodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "episode_id", nullable = false, unique = true)
    private UUID episodeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "order_id")
    private UUID orderId;

@Column(name = "issue_id")
    private UUID issueId;

@Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

@Column(name = "status")
    private String status;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "ward_id")
    private UUID wardId;

@Column(name = "started_by")
    private String startedBy;

@Column(name = "jurisdiction", nullable = false)
    private String jurisdiction = "ZW";

@Column(name = "started_at")
    private OffsetDateTime startedAt;

@Column(name = "completed_at")
    private OffsetDateTime completedAt;

@Column(name = "blood_unit_id")
    private UUID bloodUnitId;

@Column(name = "patient_verified", nullable = false)
    private Boolean patientVerified = false;

@Column(name = "patient_verification_method")
    private String patientVerificationMethod;

@Column(name = "patient_biometric_ref")
    private String patientBiometricRef;

@Column(name = "patient_verified_at")
    private OffsetDateTime patientVerifiedAt;

@Column(name = "patient_verified_by")
    private String patientVerifiedBy;

@Column(name = "unit_verified", nullable = false)
    private Boolean unitVerified = false;

@Column(name = "unit_verification_method")
    private String unitVerificationMethod;

@Column(name = "unit_scan_ref")
    private String unitScanRef;

@Column(name = "unit_verified_at")
    private OffsetDateTime unitVerifiedAt;

@Column(name = "unit_verified_by")
    private String unitVerifiedBy;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "pre_transfusion_checks_json", columnDefinition = "jsonb")
    private Map<String, Object> preTransfusionChecksJson;

@Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (episodeId == null) {
            episodeId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getIssueId() { return issueId; }
    public void setIssueId(UUID issueId) { this.issueId = issueId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getWardId() { return wardId; }
    public void setWardId(UUID wardId) { this.wardId = wardId; }

    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public UUID getBloodUnitId() { return bloodUnitId; }
    public void setBloodUnitId(UUID bloodUnitId) { this.bloodUnitId = bloodUnitId; }

    public Boolean getPatientVerified() { return patientVerified; }
    public void setPatientVerified(Boolean patientVerified) { this.patientVerified = patientVerified; }

    public String getPatientVerificationMethod() { return patientVerificationMethod; }
    public void setPatientVerificationMethod(String patientVerificationMethod) {
        this.patientVerificationMethod = patientVerificationMethod;
    }

    public String getPatientBiometricRef() { return patientBiometricRef; }
    public void setPatientBiometricRef(String patientBiometricRef) { this.patientBiometricRef = patientBiometricRef; }

    public OffsetDateTime getPatientVerifiedAt() { return patientVerifiedAt; }
    public void setPatientVerifiedAt(OffsetDateTime patientVerifiedAt) { this.patientVerifiedAt = patientVerifiedAt; }

    public String getPatientVerifiedBy() { return patientVerifiedBy; }
    public void setPatientVerifiedBy(String patientVerifiedBy) { this.patientVerifiedBy = patientVerifiedBy; }

    public Boolean getUnitVerified() { return unitVerified; }
    public void setUnitVerified(Boolean unitVerified) { this.unitVerified = unitVerified; }

    public String getUnitVerificationMethod() { return unitVerificationMethod; }
    public void setUnitVerificationMethod(String unitVerificationMethod) {
        this.unitVerificationMethod = unitVerificationMethod;
    }

    public String getUnitScanRef() { return unitScanRef; }
    public void setUnitScanRef(String unitScanRef) { this.unitScanRef = unitScanRef; }

    public OffsetDateTime getUnitVerifiedAt() { return unitVerifiedAt; }
    public void setUnitVerifiedAt(OffsetDateTime unitVerifiedAt) { this.unitVerifiedAt = unitVerifiedAt; }

    public String getUnitVerifiedBy() { return unitVerifiedBy; }
    public void setUnitVerifiedBy(String unitVerifiedBy) { this.unitVerifiedBy = unitVerifiedBy; }

    public Map<String, Object> getPreTransfusionChecksJson() { return preTransfusionChecksJson; }
    public void setPreTransfusionChecksJson(Map<String, Object> preTransfusionChecksJson) {
        this.preTransfusionChecksJson = preTransfusionChecksJson;
    }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}