package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Proof of place (FJ3/FJ4, D-L6): a facility verification case. Evidence is
 * document-service refs only; for video walkthroughs the decision is retained,
 * never the recording. Verifier-decided; the outcome feeds the EXISTENCE /
 * GEOSPATIAL trust dimensions.
 */
@Entity
@Table(name = "facility_verification_case", schema = "tuso")
public class FacilityVerificationCaseEntity {

    public static final Set<String> TYPES = Set.of(
            "GUIDED_PHOTO", "VIDEO_WALKTHROUGH", "IN_PERSON", "DOCUMENT");

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_INCONCLUSIVE = "INCONCLUSIVE";
    public static final String STATUS_MORE_EVIDENCE = "MORE_EVIDENCE";
    public static final String STATUS_FAILED = "FAILED";
    public static final Set<String> DECISIONS = Set.of(
            STATUS_VERIFIED, STATUS_INCONCLUSIVE, STATUS_MORE_EVIDENCE, STATUS_FAILED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_ref", nullable = false, length = 40)
    private String caseRef;

    @Column(name = "facility_uuid", nullable = false)
    private UUID facilityUuid;

    @Column(name = "verification_type", nullable = false, length = 32)
    private String verificationType;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    private String evidenceRefs;

    @Column(name = "claimed_latitude", precision = 10, scale = 7)
    private BigDecimal claimedLatitude;

    @Column(name = "claimed_longitude", precision = 10, scale = 7)
    private BigDecimal claimedLongitude;

    @Column(name = "gps_distance_meters", precision = 12, scale = 2)
    private BigDecimal gpsDistanceMeters;

    @Column(name = "rtc_session_ref", length = 255)
    private String rtcSessionRef;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_OPEN;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCaseRef() { return caseRef; }
    public void setCaseRef(String caseRef) { this.caseRef = caseRef; }
    public UUID getFacilityUuid() { return facilityUuid; }
    public void setFacilityUuid(UUID facilityUuid) { this.facilityUuid = facilityUuid; }
    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String verificationType) { this.verificationType = verificationType; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(String evidenceRefs) { this.evidenceRefs = evidenceRefs; }
    public BigDecimal getClaimedLatitude() { return claimedLatitude; }
    public void setClaimedLatitude(BigDecimal claimedLatitude) { this.claimedLatitude = claimedLatitude; }
    public BigDecimal getClaimedLongitude() { return claimedLongitude; }
    public void setClaimedLongitude(BigDecimal claimedLongitude) { this.claimedLongitude = claimedLongitude; }
    public BigDecimal getGpsDistanceMeters() { return gpsDistanceMeters; }
    public void setGpsDistanceMeters(BigDecimal gpsDistanceMeters) { this.gpsDistanceMeters = gpsDistanceMeters; }
    public String getRtcSessionRef() { return rtcSessionRef; }
    public void setRtcSessionRef(String rtcSessionRef) { this.rtcSessionRef = rtcSessionRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
