package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * See V210__emergency_identity_link.sql for the full rationale. Append-only: rows are never
 * updated except to set {@code supersededBy} when a later resolution corrects this one.
 */
@Entity
@Table(name = "emergency_identity_link", schema = "pct")
public class EmergencyIdentityLinkEntity {

    @Id
    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "previous_identity_mode", nullable = false)
    private String previousIdentityMode;

    @Column(name = "previous_subject_cpid")
    private String previousSubjectCpid;

    @Column(name = "resolved_subject_cpid", nullable = false)
    private String resolvedSubjectCpid;

    @Column(name = "resolution_method", nullable = false)
    private String resolutionMethod;

    @Column(name = "evidence_ref")
    private String evidenceRef;

    @Column(name = "vito_merge_id")
    private String vitoMergeId;

    @Column(name = "resolved_by", nullable = false)
    private String resolvedBy;

    @Column(name = "resolved_at", nullable = false)
    private OffsetDateTime resolvedAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID v) { this.linkId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getPreviousIdentityMode() { return previousIdentityMode; }
    public void setPreviousIdentityMode(String v) { this.previousIdentityMode = v; }
    public String getPreviousSubjectCpid() { return previousSubjectCpid; }
    public void setPreviousSubjectCpid(String v) { this.previousSubjectCpid = v; }
    public String getResolvedSubjectCpid() { return resolvedSubjectCpid; }
    public void setResolvedSubjectCpid(String v) { this.resolvedSubjectCpid = v; }
    public String getResolutionMethod() { return resolutionMethod; }
    public void setResolutionMethod(String v) { this.resolutionMethod = v; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String v) { this.evidenceRef = v; }
    public String getVitoMergeId() { return vitoMergeId; }
    public void setVitoMergeId(String v) { this.vitoMergeId = v; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String v) { this.resolvedBy = v; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime v) { this.resolvedAt = v; }
    public UUID getSupersededBy() { return supersededBy; }
    public void setSupersededBy(UUID v) { this.supersededBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
