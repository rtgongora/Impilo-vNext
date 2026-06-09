package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wellness_routes", schema = "simba")
public class WellnessRouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false, unique = true)
    private UUID routeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "route_code", nullable = false, length = 64)
    private String routeCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "facility_name")
    private String facilityName;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "elevation_m")
    private Integer elevationM;

    @Column(name = "difficulty", length = 16)
    private String difficulty;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (routeId == null) {
            routeId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public String getTitle() {
        return title;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public Integer getElevationM() {
        return elevationM;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
