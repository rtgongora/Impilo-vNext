package zw.gov.mohcc.impilo.reporting.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-case theatre metric projection (Wave 4 §20). Upserted by {@code TheatreReportingConsumer} from
 * the theatre.* event stream; the seeded theatre report catalog aggregates from this table so reports
 * reconcile with the cases actually run.
 */
@Entity
@Table(name = "rpt_theatre_case_metric")
public class RptTheatreCaseMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "procedure_code", length = 64)
    private String procedureCode;

    @Column(name = "procedure_name", length = 255)
    private String procedureName;

    @Column(name = "status", length = 24)
    private String status;

    @Column(name = "theatre_minutes")
    private Integer theatreMinutes;

    @Column(name = "has_anaesthesia", nullable = false)
    private boolean hasAnaesthesia = false;

    @Column(name = "implant_count", nullable = false)
    private int implantCount = 0;

    @Column(name = "consumable_count", nullable = false)
    private int consumableCount = 0;

    @Column(name = "blood_units", nullable = false)
    private int bloodUnits = 0;

    @Column(name = "cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "returned_to_theatre", nullable = false)
    private boolean returnedToTheatre = false;

    @Column(name = "unplanned_icu", nullable = false)
    private boolean unplannedIcu = false;

    @Column(name = "pacu_destination", length = 24)
    private String pacuDestination;

    @Column(name = "safety_event_count", nullable = false)
    private int safetyEventCount = 0;

    @Column(name = "complication_flag", nullable = false)
    private boolean complicationFlag = false;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String v) { this.procedureCode = v; }
    public String getProcedureName() { return procedureName; }
    public void setProcedureName(String v) { this.procedureName = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getTheatreMinutes() { return theatreMinutes; }
    public void setTheatreMinutes(Integer v) { this.theatreMinutes = v; }
    public boolean isHasAnaesthesia() { return hasAnaesthesia; }
    public void setHasAnaesthesia(boolean v) { this.hasAnaesthesia = v; }
    public int getImplantCount() { return implantCount; }
    public void setImplantCount(int v) { this.implantCount = v; }
    public int getConsumableCount() { return consumableCount; }
    public void setConsumableCount(int v) { this.consumableCount = v; }
    public int getBloodUnits() { return bloodUnits; }
    public void setBloodUnits(int v) { this.bloodUnits = v; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean v) { this.cancelled = v; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String v) { this.cancellationReason = v; }
    public boolean isReturnedToTheatre() { return returnedToTheatre; }
    public void setReturnedToTheatre(boolean v) { this.returnedToTheatre = v; }
    public boolean isUnplannedIcu() { return unplannedIcu; }
    public void setUnplannedIcu(boolean v) { this.unplannedIcu = v; }
    public String getPacuDestination() { return pacuDestination; }
    public void setPacuDestination(String v) { this.pacuDestination = v; }
    public int getSafetyEventCount() { return safetyEventCount; }
    public void setSafetyEventCount(int v) { this.safetyEventCount = v; }
    public boolean isComplicationFlag() { return complicationFlag; }
    public void setComplicationFlag(boolean v) { this.complicationFlag = v; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
