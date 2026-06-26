package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A clinical/practical placement with preceptor sign-off (V021). */
@Entity
@Table(name = "lrn_clinical_placement")
public class ClinicalPlacementEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_profile_id", nullable = false)
    private UUID studentProfileId;

    @Column(name = "term_id")
    private UUID termId;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "preceptor_facilitator_id")
    private UUID preceptorFacilitatorId;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "signoff_status", nullable = false, length = 32)
    private String signoffStatus = "PENDING";

    @Column(name = "signed_off_by", length = 255)
    private String signedOffBy;

    @Column(name = "signed_off_at")
    private OffsetDateTime signedOffAt;

    @Column(name = "signoff_notes", columnDefinition = "TEXT")
    private String signoffNotes;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getStudentProfileId() { return studentProfileId; }
    public void setStudentProfileId(UUID studentProfileId) { this.studentProfileId = studentProfileId; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public UUID getVenueId() { return venueId; }
    public void setVenueId(UUID venueId) { this.venueId = venueId; }
    public UUID getPreceptorFacilitatorId() { return preceptorFacilitatorId; }
    public void setPreceptorFacilitatorId(UUID preceptorFacilitatorId) { this.preceptorFacilitatorId = preceptorFacilitatorId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }
    public String getSignoffStatus() { return signoffStatus; }
    public void setSignoffStatus(String signoffStatus) { this.signoffStatus = signoffStatus; }
    public String getSignedOffBy() { return signedOffBy; }
    public void setSignedOffBy(String signedOffBy) { this.signedOffBy = signedOffBy; }
    public OffsetDateTime getSignedOffAt() { return signedOffAt; }
    public void setSignedOffAt(OffsetDateTime signedOffAt) { this.signedOffAt = signedOffAt; }
    public String getSignoffNotes() { return signoffNotes; }
    public void setSignoffNotes(String signoffNotes) { this.signoffNotes = signoffNotes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
