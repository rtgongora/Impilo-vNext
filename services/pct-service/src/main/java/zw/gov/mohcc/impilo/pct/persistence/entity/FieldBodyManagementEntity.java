package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Non-facility (field) body management for a death case — the branch for a body that may never come
 * to a facility. Records safe &amp; dignified burial (WHO safe-and-dignified-burial protocol; IFRC
 * safe handling of bodies during outbreaks), family release, funeral-director direct handover, police
 * handover, or an unrecovered body. This is NOT mortuary custody ({@link BodyCustodyEntity}); a case
 * may carry this and no custody row at all. Owned inside pct-service.
 */
@Entity
@Table(name = "pct_field_body_management")
public class FieldBodyManagementEntity {

    @Id
    @Column(name = "fbm_id", nullable = false)
    private UUID fbmId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "facility_id")
    private UUID facilityId;

    /** SAFE_AND_DIGNIFIED_BURIAL|FAMILY_RELEASE|FUNERAL_DIRECTOR|POLICE_HANDOVER|UNRECOVERED */
    @Column(name = "disposition_type", nullable = false)
    private String dispositionType;

    @Column(name = "infectious_risk", nullable = false)
    private boolean infectiousRisk = false;

    @Column(name = "infectious_hazard")
    private String infectiousHazard;

    @Column(name = "safe_burial_protocol_applied", nullable = false)
    private boolean safeBurialProtocolApplied = false;

    @Column(name = "ppe_used")
    private Boolean ppeUsed;

    @Column(name = "handling_team")
    private String handlingTeam;

    @Column(name = "family_present")
    private Boolean familyPresent;

    @Column(name = "religious_rites_observed")
    private Boolean religiousRitesObserved;

    @Column(name = "released_to_name")
    private String releasedToName;

    @Column(name = "released_to_relationship")
    private String releasedToRelationship;

    @Column(name = "location")
    private String location;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "managed_by")
    private String managedBy;

    @Column(name = "managed_by_role")
    private String managedByRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getFbmId() { return fbmId; }
    public void setFbmId(UUID fbmId) { this.fbmId = fbmId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getDispositionType() { return dispositionType; }
    public void setDispositionType(String dispositionType) { this.dispositionType = dispositionType; }
    public boolean isInfectiousRisk() { return infectiousRisk; }
    public void setInfectiousRisk(boolean infectiousRisk) { this.infectiousRisk = infectiousRisk; }
    public String getInfectiousHazard() { return infectiousHazard; }
    public void setInfectiousHazard(String infectiousHazard) { this.infectiousHazard = infectiousHazard; }
    public boolean isSafeBurialProtocolApplied() { return safeBurialProtocolApplied; }
    public void setSafeBurialProtocolApplied(boolean safeBurialProtocolApplied) { this.safeBurialProtocolApplied = safeBurialProtocolApplied; }
    public Boolean getPpeUsed() { return ppeUsed; }
    public void setPpeUsed(Boolean ppeUsed) { this.ppeUsed = ppeUsed; }
    public String getHandlingTeam() { return handlingTeam; }
    public void setHandlingTeam(String handlingTeam) { this.handlingTeam = handlingTeam; }
    public Boolean getFamilyPresent() { return familyPresent; }
    public void setFamilyPresent(Boolean familyPresent) { this.familyPresent = familyPresent; }
    public Boolean getReligiousRitesObserved() { return religiousRitesObserved; }
    public void setReligiousRitesObserved(Boolean religiousRitesObserved) { this.religiousRitesObserved = religiousRitesObserved; }
    public String getReleasedToName() { return releasedToName; }
    public void setReleasedToName(String releasedToName) { this.releasedToName = releasedToName; }
    public String getReleasedToRelationship() { return releasedToRelationship; }
    public void setReleasedToRelationship(String releasedToRelationship) { this.releasedToRelationship = releasedToRelationship; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getManagedBy() { return managedBy; }
    public void setManagedBy(String managedBy) { this.managedBy = managedBy; }
    public String getManagedByRole() { return managedByRole; }
    public void setManagedByRole(String managedByRole) { this.managedByRole = managedByRole; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
