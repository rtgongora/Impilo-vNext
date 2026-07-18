package zw.gov.mohcc.impilo.tshepo.identity.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps a Health ID to a canonical CPID (Clinical Patient Identifier).
 *
 * <p>This table is the authoritative source for the Health ID ↔ CPID relationship
 * within a tenant — and the ONLY place the two identifiers co-reside (Identity
 * Contract §7). The CPID is an independent random UUID v4; idempotency comes from
 * the unique (tenant_id, health_id) constraint, not from derivation.</p>
 *
 * <p>No PII is stored — Health ID is an opaque UUID issued by VITO.</p>
 */
@Entity
@Table(name = "id_mapping", schema = "tshepo_identity",
       uniqueConstraints = @UniqueConstraint(name = "uq_id_mapping_tenant_health",
                                              columnNames = {"tenant_id", "health_id"}))
public class IdMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Column(name = "cpid", nullable = false, unique = true)
    private UUID cpid;

    @Column(name = "crid")
    private UUID crid;

    @Column(name = "mapping_status", nullable = false, length = 16)
    private String mappingStatus = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }

    public UUID getCpid() { return cpid; }
    public void setCpid(UUID cpid) { this.cpid = cpid; }

    public UUID getCrid() { return crid; }
    public void setCrid(UUID crid) { this.crid = crid; }

    public String getMappingStatus() { return mappingStatus; }
    public void setMappingStatus(String mappingStatus) { this.mappingStatus = mappingStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
