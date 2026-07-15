package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_orders", schema = "madi")
public class BloodOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "oros_order_ref")
    private String orosOrderRef;

@Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

@Column(name = "blood_group", nullable = false)
    private String bloodGroup;

@Column(name = "component_type", nullable = false)
    private String componentType;

@Column(name = "units_requested")
    private Integer unitsRequested;

@Column(name = "status")
    private String status;

@Column(name = "priority")
    private String priority;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "ordering_provider")
    private String orderingProvider;

// Honour the schema default ('ZW'). Hibernate always emits the column on INSERT, so a null Java
    // field is written as SQL NULL and overrides the DB DEFAULT — which violated the NOT NULL constraint
    // and made every inpatient/theatre blood order fail (best-effort → null madi_order_id). Default here.
    @Column(name = "jurisdiction", nullable = false)
    private String jurisdiction = "ZW";

@Column(name = "encounter_ref")
    private String encounterRef;

    @Column(name = "oros_integration_status")
    private String orosIntegrationStatus;

    /** Canonical trauma-episode correlation id (DAIDZAI-minted). Null for non-trauma blood orders. */
    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

@Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (orderId == null) {
            orderId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getOrosOrderRef() { return orosOrderRef; }
    public void setOrosOrderRef(String orosOrderRef) { this.orosOrderRef = orosOrderRef; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public Integer getUnitsRequested() { return unitsRequested; }
    public void setUnitsRequested(Integer unitsRequested) { this.unitsRequested = unitsRequested; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getOrderingProvider() { return orderingProvider; }
    public void setOrderingProvider(String orderingProvider) { this.orderingProvider = orderingProvider; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String encounterRef) { this.encounterRef = encounterRef; }

    public String getOrosIntegrationStatus() { return orosIntegrationStatus; }
    public void setOrosIntegrationStatus(String orosIntegrationStatus) { this.orosIntegrationStatus = orosIntegrationStatus; }

    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}