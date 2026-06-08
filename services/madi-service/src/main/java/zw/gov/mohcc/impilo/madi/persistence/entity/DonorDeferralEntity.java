package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "donor_deferrals", schema = "madi")
public class DonorDeferralEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deferral_id", nullable = false, unique = true)
    private UUID deferralId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "donor_id")
    private UUID donorId;

@Column(name = "reason_code", nullable = false)
    private String reasonCode;

@Column(name = "status")
    private String status;

@Column(name = "deferred_until")
    private LocalDate deferredUntil;

@Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

@Column(name = "deferred_by")
    private String deferredBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (deferralId == null) {
            deferralId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getDeferralId() { return deferralId; }
    public void setDeferralId(UUID deferralId) { this.deferralId = deferralId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDonorId() { return donorId; }
    public void setDonorId(UUID donorId) { this.donorId = donorId; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getDeferredUntil() { return deferredUntil; }
    public void setDeferredUntil(LocalDate deferredUntil) { this.deferredUntil = deferredUntil; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDeferredBy() { return deferredBy; }
    public void setDeferredBy(String deferredBy) { this.deferredBy = deferredBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}