package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * APPEND-ONLY record of an IATG adjudication decision (identity / provider /
 * facility / organization claim adjudication).
 *
 * <p>Rows are immutable once persisted: there is no update path in the service
 * layer, no {@code @PreUpdate} hook, and migration V008 installs a database
 * trigger that rejects UPDATE/DELETE. A decision is superseded by inserting a
 * NEW row for the same (subjectType, subjectRef); the effective decision is
 * the newest row with {@code effectiveAt <= now} and ({@code expiresAt} null
 * or in the future). Full history is preserved for audit.</p>
 *
 * <p>Optionally references the workflow-service adjudication instance
 * ({@code workflow_instance_id} -&gt; {@code wf_instances.instance_id}) that
 * produced the decision — see workflow-service V003 seed definitions and
 * docs/architecture/iatg-adjudication-contract.md.</p>
 */
@Entity
@Table(name = "wgv_adjudication_decision")
public class AdjudicationDecisionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** wf_instances.instance_id in workflow-service; null for manual/bootstrap decisions. */
    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    /** IDENTITY | PROVIDER | FACILITY | ORGANIZATION */
    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_ref", nullable = false, length = 255)
    private String subjectRef;

    /** APPROVED | DENIED | CONDITIONAL */
    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    /** Mandatory adjudication rationale. */
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "authority_user_id", length = 255)
    private String authorityUserId;

    @Column(name = "authority_role", length = 128)
    private String authorityRole;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions")
    private Map<String, Object> permissions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "restrictions")
    private Map<String, Object> restrictions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs")
    private List<String> evidenceRefs;

    @Column(name = "access_request_id")
    private UUID accessRequestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (effectiveAt == null) effectiveAt = createdAt;
    }

    // No @PreUpdate: this entity is append-only by doctrine.

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(UUID workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAuthorityUserId() { return authorityUserId; }
    public void setAuthorityUserId(String authorityUserId) { this.authorityUserId = authorityUserId; }
    public String getAuthorityRole() { return authorityRole; }
    public void setAuthorityRole(String authorityRole) { this.authorityRole = authorityRole; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Map<String, Object> getPermissions() { return permissions; }
    public void setPermissions(Map<String, Object> permissions) { this.permissions = permissions; }
    public Map<String, Object> getRestrictions() { return restrictions; }
    public void setRestrictions(Map<String, Object> restrictions) { this.restrictions = restrictions; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> evidenceRefs) { this.evidenceRefs = evidenceRefs; }
    public UUID getAccessRequestId() { return accessRequestId; }
    public void setAccessRequestId(UUID accessRequestId) { this.accessRequestId = accessRequestId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
