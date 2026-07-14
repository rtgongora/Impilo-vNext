package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A registered bed. TUSO owns the REGISTRY / capacity truth (what beds exist and
 * their class). inpatient.bed remains the OCCUPANCY SoR (who is in a bed now).
 */
@Entity
@Table(name = "bed", schema = "tuso")
public class BedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "clinical_space_id", nullable = false)
    private UUID clinicalSpaceId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "bed_class", nullable = false, length = 24)
    private String bedClass;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "AVAILABLE";

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
        out.put("clinicalSpaceId", clinicalSpaceId != null ? clinicalSpaceId.toString() : null);
        out.put("code", code);
        out.put("bedClass", bedClass);
        out.put("status", status);
        return out;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClinicalSpaceId() { return clinicalSpaceId; }
    public void setClinicalSpaceId(UUID clinicalSpaceId) { this.clinicalSpaceId = clinicalSpaceId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getBedClass() { return bedClass; }
    public void setBedClass(String bedClass) { this.bedClass = bedClass; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
