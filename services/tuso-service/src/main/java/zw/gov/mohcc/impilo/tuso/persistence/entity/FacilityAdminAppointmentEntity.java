package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A facility administrator appointment (IATG Wave 2, WS-E facility-claim lane).
 *
 * <p>Mirrors {@link PractitionerInChargeAssignmentEntity} (facility link + role + approval state
 * defaulting to {@code PENDING}) but keys the subject on the <b>canonical facility UUID</b>
 * ({@link FacilityEntity#getFacilityUuid()}), never a Health ID: a facility is <em>administered</em>,
 * not an identity anchor that gets rebound. A facility may have many administrators over time, so
 * this is an append-only appointment record — there is no recover-not-reissue analogue.</p>
 */
@Entity
@Table(name = "facility_admin_appointment", schema = "tuso")
public class FacilityAdminAppointmentEntity {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_REJECTED = "REJECTED";
    public static final String STATE_REVOKED = "REVOKED";

    public static final String ROLE_FACILITY_ADMINISTRATOR = "FACILITY_ADMINISTRATOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Canonical facility UUID ({@code tuso.facility.facility_uuid}) — the appointment subject. */
    @Column(name = "facility_uuid", nullable = false)
    private UUID facilityUuid;

    /** The person (Health ID) being appointed as an administrator of the facility. */
    @Column(name = "person_health_id", nullable = false, length = 128)
    private String personHealthId;

    @Column(name = "role", nullable = false, length = 64)
    private String role = ROLE_FACILITY_ADMINISTRATOR;

    /** Who submitted/appointed (actor id); provenance only, never the subject. */
    @Column(name = "appointed_by", length = 255)
    private String appointedBy;

    @Column(name = "approval_state", nullable = false, length = 64)
    private String approvalState = STATE_PENDING;

    @Column(name = "evidence_ref", length = 512)
    private String evidenceRef;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

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
    public void setId(Long id) { this.id = id; }
    public UUID getFacilityUuid() { return facilityUuid; }
    public void setFacilityUuid(UUID facilityUuid) { this.facilityUuid = facilityUuid; }
    public String getPersonHealthId() { return personHealthId; }
    public void setPersonHealthId(String personHealthId) { this.personHealthId = personHealthId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAppointedBy() { return appointedBy; }
    public void setAppointedBy(String appointedBy) { this.appointedBy = appointedBy; }
    public String getApprovalState() { return approvalState; }
    public void setApprovalState(String approvalState) { this.approvalState = approvalState; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
