package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Native Fundo pathway (Phase 5A) — groups native courses into a structured
 * journey. Distinct from the legacy {@link LearningPathEntity}, which groups
 * legacy {@code lrn_learning_resource} rows.
 */
@Entity
@Table(
        name = "lrn_fundo_pathway",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lrn_fundo_pathway_code",
                columnNames = {"tenant_id", "code"}))
public class FundoPathwayEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 160)
    private String code;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_cadres", columnDefinition = "TEXT")
    private String targetCadres;

    @Column(name = "target_roles", columnDefinition = "TEXT")
    private String targetRoles;

    @Column(name = "target_facility_levels", columnDefinition = "TEXT")
    private String targetFacilityLevels;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTargetCadres() { return targetCadres; }
    public void setTargetCadres(String targetCadres) { this.targetCadres = targetCadres; }
    public String getTargetRoles() { return targetRoles; }
    public void setTargetRoles(String targetRoles) { this.targetRoles = targetRoles; }
    public String getTargetFacilityLevels() { return targetFacilityLevels; }
    public void setTargetFacilityLevels(String t) { this.targetFacilityLevels = t; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
