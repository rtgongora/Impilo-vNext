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
    /** ACTIVE appointment whose valid_to has passed (expiry sweep, V030). */
    public static final String STATE_EXPIRED = "EXPIRED";

    public static final String ROLE_FACILITY_ADMINISTRATOR = "FACILITY_ADMINISTRATOR";

    /** Closed role-scope vocabulary (Place Journey Doctrine FJ1, D-L4; V030 CHECK). */
    public static final java.util.Set<String> ROLE_SCOPES = java.util.Set.of(
            "FACILITY_VIEWER", "DATA_STEWARD", "SERVICE_CONFIG_MANAGER",
            "FACILITY_ADMINISTRATOR", "REGULATORY_LIAISON");

    public static final String CLAIM_TYPE_NEW = "NEW";
    /** FJ8: recovery re-establishes access on the SAME facility — never a new record. */
    public static final String CLAIM_TYPE_RECOVERY = "RECOVERY";

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

    /** What the claimant says they are to the facility (FCV-W4, closed vocabulary). */
    @jakarta.persistence.Column(name = "relationship_type", length = 64)
    private String relationshipType;

    /** Resolved provider public id — present only for a regulated relationship. */
    @jakarta.persistence.Column(name = "provider_public_id", length = 64)
    private String providerPublicId;

    @jakarta.persistence.Column(name = "justification", columnDefinition = "text")
    private String justification;

    /** NEW | RECOVERY (FJ8). */
    @Column(name = "claim_type", nullable = false, length = 16)
    private String claimType = CLAIM_TYPE_NEW;

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

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
