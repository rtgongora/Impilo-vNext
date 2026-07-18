package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Provenance row for a single bulk-imported legacy PHID (Identity Contract §14).
 * Records the source, the reconciliation disposition, and (where assigned) the
 * canonical Health ID it was linked/minted to.
 */
@Entity
@Table(name = "legacy_phid_import", schema = "vito")
public class LegacyPhidImportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "phid", nullable = false, length = 16)
    private String phid;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "source_facility_id", length = 64)
    private String sourceFacilityId;

    @Column(name = "issuance_date")
    private LocalDate issuanceDate;

    /** ASSIGNED_LINKED | ASSIGNED_NEW | RESERVED_POOL | QUARANTINED | INVALID_FORMAT */
    @Column(name = "disposition", nullable = false, length = 24)
    private String disposition;

    @Column(name = "health_id")
    private UUID healthId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @PrePersist
    void onCreate() {
        if (importedAt == null) importedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public String getPhid() { return phid; }
    public void setPhid(String phid) { this.phid = phid; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceFacilityId() { return sourceFacilityId; }
    public void setSourceFacilityId(String sourceFacilityId) { this.sourceFacilityId = sourceFacilityId; }
    public LocalDate getIssuanceDate() { return issuanceDate; }
    public void setIssuanceDate(LocalDate issuanceDate) { this.issuanceDate = issuanceDate; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getImportedAt() { return importedAt; }
}
