package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_mpdsr_review", schema = "rito")
public class MpdsrReviewEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "death_case_id", nullable = false)
    private UUID deathCaseId;

    @Column(name = "near_miss_ref", length = 128)
    private String nearMissRef;

    @Column(name = "who_classification", length = 64)
    private String whoClassification;

    @Column(name = "review_level", nullable = false, length = 32)
    private String reviewLevel;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "district_id")
    private UUID districtId;

    @Column(name = "trigger_flags", length = 256)
    private String triggerFlags;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (openedAt == null) openedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "OPEN";
        if (reviewLevel == null) reviewLevel = "FACILITY";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getDeathCaseId() { return deathCaseId; }
    public void setDeathCaseId(UUID deathCaseId) { this.deathCaseId = deathCaseId; }
    public String getNearMissRef() { return nearMissRef; }
    public void setNearMissRef(String nearMissRef) { this.nearMissRef = nearMissRef; }
    public String getWhoClassification() { return whoClassification; }
    public void setWhoClassification(String whoClassification) { this.whoClassification = whoClassification; }
    public String getReviewLevel() { return reviewLevel; }
    public void setReviewLevel(String reviewLevel) { this.reviewLevel = reviewLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getDistrictId() { return districtId; }
    public void setDistrictId(UUID districtId) { this.districtId = districtId; }
    public String getTriggerFlags() { return triggerFlags; }
    public void setTriggerFlags(String triggerFlags) { this.triggerFlags = triggerFlags; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime openedAt) { this.openedAt = openedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
