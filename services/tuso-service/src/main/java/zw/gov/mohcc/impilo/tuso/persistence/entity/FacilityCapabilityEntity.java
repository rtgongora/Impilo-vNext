package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "facility_capability", schema = "tuso")
public class FacilityCapabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "capability_code", nullable = false, length = 100)
    private String capabilityCode;

    @Column(name = "capability_type", nullable = false, length = 50)
    private String capabilityType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "zibo_validated", nullable = false)
    private Boolean ziboValidated = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operating_hours", columnDefinition = "jsonb")
    private Map<String, Object> operatingHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCapabilityCode() { return capabilityCode; }
    public void setCapabilityCode(String capabilityCode) { this.capabilityCode = capabilityCode; }

    public String getCapabilityType() { return capabilityType; }
    public void setCapabilityType(String capabilityType) { this.capabilityType = capabilityType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getZiboValidated() { return ziboValidated; }
    public void setZiboValidated(Boolean ziboValidated) { this.ziboValidated = ziboValidated; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Map<String, Object> getOperatingHours() { return operatingHours; }
    public void setOperatingHours(Map<String, Object> operatingHours) { this.operatingHours = operatingHours; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
