package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Canonical health-programme registry entry (WGV SoR). See
 * V014__programme_registry.sql for the ownership rationale.
 */
@Entity
@Table(name = "wgv_programme")
public class ProgrammeEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "programme_code", nullable = false, length = 64)
    private String programmeCode;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "programme_type", nullable = false, length = 64)
    private String programmeType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "scope_level", nullable = false, length = 32)
    private String scopeLevel;

    @Column(name = "parent_programme_id")
    private UUID parentProgrammeId;

    @Column(name = "responsible_organisation_id")
    private UUID responsibleOrganisationId;

    @Column(name = "accountable_office", length = 512)
    private String accountableOffice;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "provenance", nullable = false, length = 32)
    private String provenance = "NATIVE";

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected ProgrammeEntity() {}

    public ProgrammeEntity(UUID id, UUID tenantId, String programmeCode, String name,
                            String programmeType, String status, String scopeLevel) {
        this.id = id;
        this.tenantId = tenantId;
        this.programmeCode = programmeCode;
        this.name = name;
        this.programmeType = programmeType;
        this.status = status;
        this.scopeLevel = scopeLevel;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getProgrammeType() { return programmeType; }
    public void setProgrammeType(String programmeType) { this.programmeType = programmeType; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public String getScopeLevel() { return scopeLevel; }
    public void setScopeLevel(String scopeLevel) { this.scopeLevel = scopeLevel; touch(); }
    public UUID getParentProgrammeId() { return parentProgrammeId; }
    public void setParentProgrammeId(UUID parentProgrammeId) { this.parentProgrammeId = parentProgrammeId; touch(); }
    public UUID getResponsibleOrganisationId() { return responsibleOrganisationId; }
    public void setResponsibleOrganisationId(UUID responsibleOrganisationId) { this.responsibleOrganisationId = responsibleOrganisationId; touch(); }
    public String getAccountableOffice() { return accountableOffice; }
    public void setAccountableOffice(String accountableOffice) { this.accountableOffice = accountableOffice; touch(); }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; touch(); }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; touch(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; touch(); }

    public boolean isActive() { return "ACTIVE".equals(status); }
}
