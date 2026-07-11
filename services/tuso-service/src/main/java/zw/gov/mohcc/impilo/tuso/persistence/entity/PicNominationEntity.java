package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "pic_nomination", schema = "tuso")
public class PicNominationEntity {

    @Id
    @Column(name = "nomination_id", nullable = false, updatable = false)
    private UUID nominationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "facility_unit_id")
    private Long facilityUnitId;

    @Column(name = "provider_public_id", nullable = false, length = 64)
    private String providerPublicId;

    @Column(name = "impilo_health_id")
    private UUID impiloHealthId;

    @Column(name = "state", nullable = false, length = 64)
    private String state = "PROPOSED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> eligibilitySnapshot;

    @Column(name = "eligibility_result", length = 32)
    private String eligibilityResult;

    @Column(name = "eligibility_snapshot_ref", length = 128)
    private String eligibilitySnapshotRef;

    @Column(name = "nominated_by", nullable = false, length = 255)
    private String nominatedBy;

    @Column(name = "nominated_at", nullable = false)
    private Instant nominatedAt;

    @Column(name = "practitioner_response", length = 32)
    private String practitionerResponse;

    @Column(name = "practitioner_response_at")
    private Instant practitionerResponseAt;

    @Column(name = "practitioner_response_notes", columnDefinition = "text")
    private String practitionerResponseNotes;

    @Column(name = "review_authority", length = 128)
    private String reviewAuthority;

    @Column(name = "review_reference", length = 128)
    private String reviewReference;

    @Column(name = "review_state", length = 32)
    private String reviewState;

    @Column(name = "review_recorded_by", length = 255)
    private String reviewRecordedBy;

    @Column(name = "review_recorded_at")
    private Instant reviewRecordedAt;

    @Column(name = "activated_assignment_id")
    private Long activatedAssignmentId;

    @Column(name = "linked_application_id")
    private UUID linkedApplicationId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (nominationId == null) {
            nominationId = UUID.randomUUID();
        }
        if (nominatedAt == null) {
            nominatedAt = Instant.now();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getNominationId() { return nominationId; }
    public void setNominationId(UUID nominationId) { this.nominationId = nominationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public Long getFacilityUnitId() { return facilityUnitId; }
    public void setFacilityUnitId(Long facilityUnitId) { this.facilityUnitId = facilityUnitId; }
    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }
    public UUID getImpiloHealthId() { return impiloHealthId; }
    public void setImpiloHealthId(UUID impiloHealthId) { this.impiloHealthId = impiloHealthId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Map<String, Object> getEligibilitySnapshot() { return eligibilitySnapshot; }
    public void setEligibilitySnapshot(Map<String, Object> eligibilitySnapshot) { this.eligibilitySnapshot = eligibilitySnapshot; }
    public String getEligibilityResult() { return eligibilityResult; }
    public void setEligibilityResult(String eligibilityResult) { this.eligibilityResult = eligibilityResult; }
    public String getEligibilitySnapshotRef() { return eligibilitySnapshotRef; }
    public void setEligibilitySnapshotRef(String eligibilitySnapshotRef) { this.eligibilitySnapshotRef = eligibilitySnapshotRef; }
    public String getNominatedBy() { return nominatedBy; }
    public void setNominatedBy(String nominatedBy) { this.nominatedBy = nominatedBy; }
    public Instant getNominatedAt() { return nominatedAt; }
    public void setNominatedAt(Instant nominatedAt) { this.nominatedAt = nominatedAt; }
    public String getPractitionerResponse() { return practitionerResponse; }
    public void setPractitionerResponse(String practitionerResponse) { this.practitionerResponse = practitionerResponse; }
    public Instant getPractitionerResponseAt() { return practitionerResponseAt; }
    public void setPractitionerResponseAt(Instant practitionerResponseAt) { this.practitionerResponseAt = practitionerResponseAt; }
    public String getPractitionerResponseNotes() { return practitionerResponseNotes; }
    public void setPractitionerResponseNotes(String practitionerResponseNotes) { this.practitionerResponseNotes = practitionerResponseNotes; }
    public String getReviewAuthority() { return reviewAuthority; }
    public void setReviewAuthority(String reviewAuthority) { this.reviewAuthority = reviewAuthority; }
    public String getReviewReference() { return reviewReference; }
    public void setReviewReference(String reviewReference) { this.reviewReference = reviewReference; }
    public String getReviewState() { return reviewState; }
    public void setReviewState(String reviewState) { this.reviewState = reviewState; }
    public String getReviewRecordedBy() { return reviewRecordedBy; }
    public void setReviewRecordedBy(String reviewRecordedBy) { this.reviewRecordedBy = reviewRecordedBy; }
    public Instant getReviewRecordedAt() { return reviewRecordedAt; }
    public void setReviewRecordedAt(Instant reviewRecordedAt) { this.reviewRecordedAt = reviewRecordedAt; }
    public Long getActivatedAssignmentId() { return activatedAssignmentId; }
    public void setActivatedAssignmentId(Long activatedAssignmentId) { this.activatedAssignmentId = activatedAssignmentId; }
    public UUID getLinkedApplicationId() { return linkedApplicationId; }
    public void setLinkedApplicationId(UUID linkedApplicationId) { this.linkedApplicationId = linkedApplicationId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
