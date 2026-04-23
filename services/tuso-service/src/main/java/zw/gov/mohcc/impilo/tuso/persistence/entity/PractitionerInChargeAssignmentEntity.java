package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "practitioner_in_charge_assignment", schema = "tuso")
public class PractitionerInChargeAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Column(name = "provider_public_id", nullable = false, length = 255)
    private String providerPublicId;

    @Column(name = "impilo_health_id")
    private UUID impiloHealthId;

    /**
     * External assignment identifier from VARAPI (source-of-truth for PIC lifecycle).
     * Allows TUSO to mirror assignment state without duplicating provider ownership.
     */
    @Column(name = "external_assignment_id")
    private Long externalAssignmentId;

    @Column(name = "role", nullable = false, length = 64)
    private String role;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "approval_state", nullable = false, length = 64)
    private String approvalState = "PENDING";

    @Column(name = "source_council_reference", length = 255)
    private String sourceCouncilReference;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }
    public UUID getImpiloHealthId() { return impiloHealthId; }
    public void setImpiloHealthId(UUID impiloHealthId) { this.impiloHealthId = impiloHealthId; }
    public Long getExternalAssignmentId() { return externalAssignmentId; }
    public void setExternalAssignmentId(Long externalAssignmentId) { this.externalAssignmentId = externalAssignmentId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getApprovalState() { return approvalState; }
    public void setApprovalState(String approvalState) { this.approvalState = approvalState; }
    public String getSourceCouncilReference() { return sourceCouncilReference; }
    public void setSourceCouncilReference(String sourceCouncilReference) { this.sourceCouncilReference = sourceCouncilReference; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
