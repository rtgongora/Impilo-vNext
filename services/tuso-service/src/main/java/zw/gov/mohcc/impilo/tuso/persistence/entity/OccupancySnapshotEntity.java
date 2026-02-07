package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "occupancy_snapshot", schema = "tuso")
public class OccupancySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ward", length = 100)
    private String ward;

    @Column(name = "total_beds", nullable = false)
    private Integer totalBeds = 0;

    @Column(name = "occupied_beds", nullable = false)
    private Integer occupiedBeds = 0;

    @Column(name = "available_beds", nullable = false)
    private Integer availableBeds = 0;

    @Column(name = "snapshot_time", nullable = false)
    private Instant snapshotTime;

    @Column(name = "source", nullable = false, length = 50)
    private String source = "PCT";

    @PrePersist
    protected void onCreate() {
        if (snapshotTime == null) {
            snapshotTime = Instant.now();
        }
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }

    public Integer getTotalBeds() { return totalBeds; }
    public void setTotalBeds(Integer totalBeds) { this.totalBeds = totalBeds; }

    public Integer getOccupiedBeds() { return occupiedBeds; }
    public void setOccupiedBeds(Integer occupiedBeds) { this.occupiedBeds = occupiedBeds; }

    public Integer getAvailableBeds() { return availableBeds; }
    public void setAvailableBeds(Integer availableBeds) { this.availableBeds = availableBeds; }

    public Instant getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(Instant snapshotTime) { this.snapshotTime = snapshotTime; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
