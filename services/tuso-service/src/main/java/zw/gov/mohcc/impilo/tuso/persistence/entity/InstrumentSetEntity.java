package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A sterilisable instrument set with its current sterilisation state. */
@Entity
@Table(name = "instrument_set", schema = "tuso")
public class InstrumentSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sterilisation_state", nullable = false, length = 24)
    private String sterilisationState = "STERILE";

    @Column(name = "sterile_until")
    private OffsetDateTime sterileUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id != null ? id.toString() : null);
        out.put("facilityId", facilityId);
        out.put("name", name);
        out.put("sterilisationState", sterilisationState);
        out.put("sterileUntil", sterileUntil != null ? sterileUntil.toString() : null);
        return out;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSterilisationState() { return sterilisationState; }
    public void setSterilisationState(String sterilisationState) { this.sterilisationState = sterilisationState; }
    public OffsetDateTime getSterileUntil() { return sterileUntil; }
    public void setSterileUntil(OffsetDateTime sterileUntil) { this.sterileUntil = sterileUntil; }
}
