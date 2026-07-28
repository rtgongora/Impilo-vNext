package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A prehabilitation/optimisation execution item (SB-1, surgical domain pack §10). One row per
 * (episode, domain) — the ENGINEERING-derived 16-domain vocabulary is declared in V005's own
 * header, since the source spec does not enumerate it in this repository.
 */
@Entity
@Table(name = "surgical_prehab_item", schema = "surgery")
public class SurgicalPrehabItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "surgical_episode_id", nullable = false)
    private UUID surgicalEpisodeId;

    @Column(nullable = false, length = 48)
    private String domain;

    @Column(nullable = false, length = 16)
    private String status = "PLANNED";

    @Column(columnDefinition = "text")
    private String target;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "registry_ref")
    private String registryRef;

    @Column(name = "planned_by")
    private String plannedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getSurgicalEpisodeId() { return surgicalEpisodeId; }
    public void setSurgicalEpisodeId(UUID v) { this.surgicalEpisodeId = v; }
    public String getDomain() { return domain; }
    public void setDomain(String v) { this.domain = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getTarget() { return target; }
    public void setTarget(String v) { this.target = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getRegistryRef() { return registryRef; }
    public void setRegistryRef(String v) { this.registryRef = v; }
    public String getPlannedBy() { return plannedBy; }
    public void setPlannedBy(String v) { this.plannedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
