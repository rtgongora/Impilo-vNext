package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "facilities")
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Column(name = "facility_type", nullable = false)
    private String facilityType;

    @Column(nullable = false)
    private String status;

    private String province;

    private String district;

    private Double latitude;

    private Double longitude;

    @Column(columnDefinition = "jsonb")
    private String capabilities;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Facility() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getFacilityType() { return facilityType; }
    public String getStatus() { return status; }
    public String getProvince() { return province; }
    public String getDistrict() { return district; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getCapabilities() { return capabilities; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
