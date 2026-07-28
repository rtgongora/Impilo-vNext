package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hand-declared emergency capability per facility (tuso V200, W10) — NOT a national trauma-centre-
 * level classification; see V200's own migration header for why. Each boolean is independently
 * nullable: absence means "not yet declared", never "no".
 */
@Entity
@Table(name = "facility_emergency_capability", schema = "tuso")
public class FacilityEmergencyCapabilityEntity {

    @Id
    @Column(name = "facility_id")
    private Long facilityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "has_24h_theatre")
    private Boolean has24hTheatre;

    @Column(name = "has_blood_bank")
    private Boolean hasBloodBank;

    @Column(name = "has_ct_scan")
    private Boolean hasCtScan;

    @Column(name = "has_icu")
    private Boolean hasIcu;

    @Column(name = "has_massive_transfusion_protocol")
    private Boolean hasMassiveTransfusionProtocol;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "declared_by", nullable = false)
    private String declaredBy;

    @Column(name = "declared_at", nullable = false)
    private Instant declaredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (declaredAt == null) declaredAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("facilityId", facilityId);
        out.put("has24hTheatre", has24hTheatre);
        out.put("hasBloodBank", hasBloodBank);
        out.put("hasCtScan", hasCtScan);
        out.put("hasIcu", hasIcu);
        out.put("hasMassiveTransfusionProtocol", hasMassiveTransfusionProtocol);
        out.put("notes", notes);
        out.put("declaredBy", declaredBy);
        out.put("declaredAt", declaredAt != null ? declaredAt.toString() : null);
        out.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return out;
    }

    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Boolean getHas24hTheatre() { return has24hTheatre; }
    public void setHas24hTheatre(Boolean has24hTheatre) { this.has24hTheatre = has24hTheatre; }
    public Boolean getHasBloodBank() { return hasBloodBank; }
    public void setHasBloodBank(Boolean hasBloodBank) { this.hasBloodBank = hasBloodBank; }
    public Boolean getHasCtScan() { return hasCtScan; }
    public void setHasCtScan(Boolean hasCtScan) { this.hasCtScan = hasCtScan; }
    public Boolean getHasIcu() { return hasIcu; }
    public void setHasIcu(Boolean hasIcu) { this.hasIcu = hasIcu; }
    public Boolean getHasMassiveTransfusionProtocol() { return hasMassiveTransfusionProtocol; }
    public void setHasMassiveTransfusionProtocol(Boolean v) { this.hasMassiveTransfusionProtocol = v; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDeclaredBy() { return declaredBy; }
    public void setDeclaredBy(String declaredBy) { this.declaredBy = declaredBy; }
    public Instant getDeclaredAt() { return declaredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
