package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A typed relationship from a problem to another clinical object. See V103__problem_links.sql. */
@Entity
@Table(name = "pct_problem_links")
public class ProblemLinkEntity {

    @Id
    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(name = "link_type", nullable = false)
    private String linkType;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    /** Set only when {@code targetType} is PROBLEM; carries a real foreign key. */
    @Column(name = "target_problem_id")
    private UUID targetProblemId;

    /** Set for every other target type; validated by the owning service, not by this schema. */
    @Column(name = "target_ref")
    private String targetRef;

    @Column(name = "note")
    private String note;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (linkId == null) linkId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID linkId) { this.linkId = linkId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getProblemId() { return problemId; }
    public void setProblemId(UUID problemId) { this.problemId = problemId; }
    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public UUID getTargetProblemId() { return targetProblemId; }
    public void setTargetProblemId(UUID targetProblemId) { this.targetProblemId = targetProblemId; }
    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
