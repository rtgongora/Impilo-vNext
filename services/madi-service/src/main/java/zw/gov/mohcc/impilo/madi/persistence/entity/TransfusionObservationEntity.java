package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfusion_observations", schema = "madi")
public class TransfusionObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "observation_id", nullable = false, unique = true)
    private UUID observationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "episode_id")
    private UUID episodeId;

@Column(name = "observation_type", nullable = false)
    private String observationType;

@Column(name = "value_numeric")
    private BigDecimal valueNumeric;

@Column(name = "value_text")
    private String valueText;

@Column(name = "unit")
    private String unit;

@Column(name = "observed_by")
    private String observedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "observed_at")
    private OffsetDateTime observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (observationId == null) {
            observationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getObservationId() { return observationId; }
    public void setObservationId(UUID observationId) { this.observationId = observationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }

    public String getObservationType() { return observationType; }
    public void setObservationType(String observationType) { this.observationType = observationType; }

    public BigDecimal getValueNumeric() { return valueNumeric; }
    public void setValueNumeric(BigDecimal valueNumeric) { this.valueNumeric = valueNumeric; }

    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getObservedBy() { return observedBy; }
    public void setObservedBy(String observedBy) { this.observedBy = observedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(OffsetDateTime observedAt) { this.observedAt = observedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}