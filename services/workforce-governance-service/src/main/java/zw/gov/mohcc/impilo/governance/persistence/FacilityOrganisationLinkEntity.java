package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wgv_facility_organisation_link")
public class FacilityOrganisationLinkEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "organisation_unit_id")
    private UUID organisationUnitId;

    @Column(name = "relationship_type", nullable = false, length = 64)
    private String relationshipType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "primary_flag", nullable = false)
    private boolean primaryFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FacilityOrganisationLinkEntity() {}

    public FacilityOrganisationLinkEntity(UUID id, UUID tenantId, Long facilityId, UUID organisationId, String relationshipType, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.facilityId = facilityId;
        this.organisationId = organisationId;
        this.relationshipType = relationshipType;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Long getFacilityId() { return facilityId; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getOrganisationUnitId() { return organisationUnitId; }
    public void setOrganisationUnitId(UUID organisationUnitId) { this.organisationUnitId = organisationUnitId; touch(); }
    public String getRelationshipType() { return relationshipType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; touch(); }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public boolean isPrimaryFlag() { return primaryFlag; }
    public void setPrimaryFlag(boolean primaryFlag) { this.primaryFlag = primaryFlag; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
