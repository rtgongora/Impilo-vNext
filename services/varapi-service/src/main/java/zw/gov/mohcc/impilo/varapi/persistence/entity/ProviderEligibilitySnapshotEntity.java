package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Point-in-time PIC eligibility assessment snapshot.
 *
 * Persists the per-axis credential picture (identity, council, registration,
 * practising certificate, licence, restrictions) exactly as evaluated at
 * {@code asOf}, so facility-side nomination decisions reference an immutable,
 * auditable artefact rather than live (mutable) credential rows.
 */
@Entity
@Table(name = "provider_eligibility_snapshot", schema = "varapi")
public class ProviderEligibilitySnapshotEntity {

    @Id
    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "provider_public_id", nullable = false, length = 64)
    private String providerPublicId;

    @Column(name = "impilo_health_id")
    private UUID impiloHealthId;

    @Column(name = "facility_ref", length = 128)
    private String facilityRef;

    @Column(name = "facility_unit_ref", length = 128)
    private String facilityUnitRef;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose = "PIC_NOMINATION";

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "axes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> axes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (snapshotId == null) {
            snapshotId = UUID.randomUUID();
        }
        if (assessedAt == null) {
            assessedAt = Instant.now();
        }
        createdAt = Instant.now();
    }

    public UUID getSnapshotId() { return snapshotId; }
    public void setSnapshotId(UUID snapshotId) { this.snapshotId = snapshotId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }

    public UUID getImpiloHealthId() { return impiloHealthId; }
    public void setImpiloHealthId(UUID impiloHealthId) { this.impiloHealthId = impiloHealthId; }

    public String getFacilityRef() { return facilityRef; }
    public void setFacilityRef(String facilityRef) { this.facilityRef = facilityRef; }

    public String getFacilityUnitRef() { return facilityUnitRef; }
    public void setFacilityUnitRef(String facilityUnitRef) { this.facilityUnitRef = facilityUnitRef; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public LocalDate getAsOf() { return asOf; }
    public void setAsOf(LocalDate asOf) { this.asOf = asOf; }

    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public Map<String, Object> getAxes() { return axes; }
    public void setAxes(Map<String, Object> axes) { this.axes = axes; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public Instant getCreatedAt() { return createdAt; }
}
