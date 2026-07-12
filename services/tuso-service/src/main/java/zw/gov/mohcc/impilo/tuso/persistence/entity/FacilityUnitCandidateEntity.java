package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A regulated-unit candidate — a review-queue proposal, DISTINCT from the real
 * {@code tuso.facility_unit}. Units are never auto-created from parent facility
 * type; a human confirms a candidate into a real unit via the existing service.
 */
@Entity
@Table(name = "facility_unit_candidate", schema = "tuso")
public class FacilityUnitCandidateEntity {

    @Id
    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "suggested_unit_type", nullable = false, length = 64)
    private String suggestedUnitType;

    @Column(name = "origin", nullable = false, length = 32)
    private String origin;

    @Column(name = "service_presence", nullable = false, length = 32)
    private String servicePresence = "UNKNOWN";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PROPOSED";

    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    @Column(name = "evidence_ref", length = 512)
    private String evidenceRef;

    @Column(name = "created_unit_id")
    private Long createdUnitId;

    @Column(name = "proposed_by", length = 255)
    private String proposedBy;

    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (candidateId == null) candidateId = UUID.randomUUID();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getSuggestedUnitType() { return suggestedUnitType; }
    public void setSuggestedUnitType(String suggestedUnitType) { this.suggestedUnitType = suggestedUnitType; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getServicePresence() { return servicePresence; }
    public void setServicePresence(String servicePresence) { this.servicePresence = servicePresence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
    public Long getCreatedUnitId() { return createdUnitId; }
    public void setCreatedUnitId(Long createdUnitId) { this.createdUnitId = createdUnitId; }
    public String getProposedBy() { return proposedBy; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
