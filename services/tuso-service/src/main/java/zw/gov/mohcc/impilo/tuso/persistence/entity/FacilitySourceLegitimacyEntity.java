package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Current legitimacy verdict for one facility from one source — one row per
 * {@code (facility, source)} (DB-enforced unique). Updates overwrite the current row; the
 * previous values are carried on the outbox event emitted by the owning service.
 *
 * <p>{@code facilityId} is the <b>canonical facility UUID</b>
 * ({@link FacilityEntity#getFacilityUuid()}), not the numeric surrogate primary key.</p>
 */
@Entity
@Table(name = "facility_source_legitimacy", schema = "tuso",
        uniqueConstraints = @UniqueConstraint(name = "uq_facility_source_legitimacy",
                columnNames = {"facility_id", "source"}))
public class FacilitySourceLegitimacyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Canonical facility UUID ({@code tuso.facility.facility_uuid}). */
    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 48)
    private FacilityLegitimacySource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private FacilityLegitimacyStatus status;

    /** When the source's verdict was established (source truth time, not row write time). */
    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    /** Pointer to the evidence backing the verdict (certificate number, registry code, document ref). */
    @Column(name = "evidence_ref", length = 512)
    private String evidenceRef;

    @Column(name = "allowed_on_platform", nullable = false)
    private boolean allowedOnPlatform = false;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "recorded_by", length = 255)
    private String recordedBy;

    /**
     * Set when this row was projected from a governed Ministry legitimacy decision. Import paths
     * must never overwrite a row carrying one — the decision is the system of record and this row
     * is only its derived projection (FCV-W0a).
     */
    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public FacilityLegitimacySource getSource() { return source; }
    public void setSource(FacilityLegitimacySource source) { this.source = source; }

    public FacilityLegitimacyStatus getStatus() { return status; }
    public void setStatus(FacilityLegitimacyStatus status) { this.status = status; }

    public Instant getAsOf() { return asOf; }
    public void setAsOf(Instant asOf) { this.asOf = asOf; }

    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }

    public boolean isAllowedOnPlatform() { return allowedOnPlatform; }
    public void setAllowedOnPlatform(boolean allowedOnPlatform) { this.allowedOnPlatform = allowedOnPlatform; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public UUID getDecisionId() { return decisionId; }
    public void setDecisionId(UUID decisionId) { this.decisionId = decisionId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
