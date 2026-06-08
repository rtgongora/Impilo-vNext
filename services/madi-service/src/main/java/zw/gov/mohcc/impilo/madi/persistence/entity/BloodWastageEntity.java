package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_wastage", schema = "madi")
public class BloodWastageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wastage_id", nullable = false, unique = true)
    private UUID wastageId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "unit_id")
    private UUID unitId;

@Column(name = "blood_bank_id")
    private UUID bloodBankId;

@Column(name = "reason_code", nullable = false)
    private String reasonCode;

@Column(name = "status")
    private String status;

@Column(name = "recorded_by")
    private String recordedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (wastageId == null) {
            wastageId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getWastageId() { return wastageId; }
    public void setWastageId(UUID wastageId) { this.wastageId = wastageId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }

    public UUID getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(UUID bloodBankId) { this.bloodBankId = bloodBankId; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}