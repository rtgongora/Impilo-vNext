package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "facility_premises_occupancy", schema = "tuso")
public class FacilityPremisesOccupancyEntity {

    @Id
    @Column(name = "occupancy_id", nullable = false, updatable = false)
    private UUID occupancyId;

    @Column(name = "premises_id", nullable = false)
    private UUID premisesId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "occupancy_role", nullable = false, length = 32)
    private String occupancyRole = "PRIMARY";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "relocation_application_id")
    private UUID relocationApplicationId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (occupancyId == null) {
            occupancyId = UUID.randomUUID();
        }
        if (effectiveFrom == null) {
            effectiveFrom = LocalDate.now();
        }
        createdAt = Instant.now();
    }

    public UUID getOccupancyId() { return occupancyId; }
    public void setOccupancyId(UUID occupancyId) { this.occupancyId = occupancyId; }
    public UUID getPremisesId() { return premisesId; }
    public void setPremisesId(UUID premisesId) { this.premisesId = premisesId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getOccupancyRole() { return occupancyRole; }
    public void setOccupancyRole(String occupancyRole) { this.occupancyRole = occupancyRole; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public UUID getRelocationApplicationId() { return relocationApplicationId; }
    public void setRelocationApplicationId(UUID relocationApplicationId) { this.relocationApplicationId = relocationApplicationId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
