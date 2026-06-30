package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionBreach;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionResolution;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A cold-chain excursion raised when a temperature reading falls outside the
 * location's safe range. Resolved as RELEASED (stock safe) or WASTAGE.
 */
@Entity
@Table(name = "inv_cold_chain_excursions")
public class ColdChainExcursionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "excursion_id", nullable = false)
    private UUID excursionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cc_location_id", nullable = false)
    private UUID ccLocationId;

    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "temperature", nullable = false)
    private double temperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "breach", nullable = false, length = 10)
    private ExcursionBreach breach;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExcursionStatus status = ExcursionStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 20)
    private ExcursionResolution resolution;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getExcursionId() { return excursionId; }
    public void setExcursionId(UUID excursionId) { this.excursionId = excursionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCcLocationId() { return ccLocationId; }
    public void setCcLocationId(UUID ccLocationId) { this.ccLocationId = ccLocationId; }

    public UUID getLogId() { return logId; }
    public void setLogId(UUID logId) { this.logId = logId; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public ExcursionBreach getBreach() { return breach; }
    public void setBreach(ExcursionBreach breach) { this.breach = breach; }

    public ExcursionStatus getStatus() { return status; }
    public void setStatus(ExcursionStatus status) { this.status = status; }

    public ExcursionResolution getResolution() { return resolution; }
    public void setResolution(ExcursionResolution resolution) { this.resolution = resolution; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
