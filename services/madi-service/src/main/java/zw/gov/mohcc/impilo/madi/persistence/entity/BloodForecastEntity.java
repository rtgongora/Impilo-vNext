package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_forecasts", schema = "madi")
public class BloodForecastEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "forecast_id", nullable = false, unique = true)
    private UUID forecastId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "blood_bank_id")
    private UUID bloodBankId;

@Column(name = "blood_group", nullable = false)
    private String bloodGroup;

@Column(name = "component_type", nullable = false)
    private String componentType;

@Column(name = "forecast_period")
    private String forecastPeriod;

@Column(name = "predicted_demand")
    private Integer predictedDemand;

@Column(name = "predicted_supply")
    private Integer predictedSupply;

@Column(name = "status")
    private String status;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "forecast_at")
    private OffsetDateTime forecastAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (forecastId == null) {
            forecastId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getForecastId() { return forecastId; }
    public void setForecastId(UUID forecastId) { this.forecastId = forecastId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(UUID bloodBankId) { this.bloodBankId = bloodBankId; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getForecastPeriod() { return forecastPeriod; }
    public void setForecastPeriod(String forecastPeriod) { this.forecastPeriod = forecastPeriod; }

    public Integer getPredictedDemand() { return predictedDemand; }
    public void setPredictedDemand(Integer predictedDemand) { this.predictedDemand = predictedDemand; }

    public Integer getPredictedSupply() { return predictedSupply; }
    public void setPredictedSupply(Integer predictedSupply) { this.predictedSupply = predictedSupply; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getForecastAt() { return forecastAt; }
    public void setForecastAt(OffsetDateTime forecastAt) { this.forecastAt = forecastAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}