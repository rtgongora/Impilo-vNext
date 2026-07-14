package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A physical clinical space (operating theatre, PACU, ICU, HDU, ward). TUSO is the
 * REGISTRY / capacity SoR. A theatre is 1:1 with a THEATRE service_point so the
 * existing booking / readiness seams keep working.
 */
@Entity
@Table(name = "clinical_space", schema = "tuso")
public class ClinicalSpaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "service_point_id")
    private UUID servicePointId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "space_type", nullable = false, length = 32)
    private String spaceType = "OPERATING_THEATRE";

    @Column(name = "capacity", nullable = false)
    private int capacity = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operating_hours", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> operatingHours = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capability_tags", columnDefinition = "jsonb", nullable = false)
    private List<String> capabilityTags = new ArrayList<>();

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (operatingHours == null) {
            operatingHours = new LinkedHashMap<>();
        }
        if (capabilityTags == null) {
            capabilityTags = new ArrayList<>();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id != null ? id.toString() : null);
        out.put("facilityId", facilityId);
        out.put("servicePointId", servicePointId != null ? servicePointId.toString() : null);
        out.put("name", name);
        out.put("spaceType", spaceType);
        out.put("capacity", capacity);
        out.put("operatingHours", operatingHours);
        out.put("capabilityTags", capabilityTags);
        out.put("status", status);
        return out;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public UUID getServicePointId() { return servicePointId; }
    public void setServicePointId(UUID servicePointId) { this.servicePointId = servicePointId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpaceType() { return spaceType; }
    public void setSpaceType(String spaceType) { this.spaceType = spaceType; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public Map<String, Object> getOperatingHours() { return operatingHours; }
    public void setOperatingHours(Map<String, Object> operatingHours) { this.operatingHours = operatingHours; }
    public List<String> getCapabilityTags() { return capabilityTags; }
    public void setCapabilityTags(List<String> capabilityTags) { this.capabilityTags = capabilityTags; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
