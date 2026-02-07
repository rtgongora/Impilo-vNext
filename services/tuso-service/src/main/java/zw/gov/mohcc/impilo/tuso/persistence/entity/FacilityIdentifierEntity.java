package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "facility_identifier", schema = "tuso",
       uniqueConstraints = @UniqueConstraint(columnNames = {"facility_id", "system", "value"}))
public class FacilityIdentifierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Column(name = "system", nullable = false, length = 100)
    private String system;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }

    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
}
