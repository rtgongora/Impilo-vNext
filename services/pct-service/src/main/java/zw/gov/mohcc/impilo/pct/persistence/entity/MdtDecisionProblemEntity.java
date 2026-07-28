package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The problems an MDT decision was actually about.
 *
 * <p>A join rather than prose: "the MDT discussed the lung lesion" cannot be linked to the problem
 * list, and a problem id can.</p>
 */
@Entity
@Table(name = "pct_mdt_decision_problems")
@IdClass(MdtDecisionProblemEntity.Key.class)
public class MdtDecisionProblemEntity {

    @Id
    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Id
    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    public UUID getDecisionId() { return decisionId; }
    public void setDecisionId(UUID v) { this.decisionId = v; }
    public UUID getProblemId() { return problemId; }
    public void setProblemId(UUID v) { this.problemId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }

    public static class Key implements Serializable {
        private UUID decisionId;
        private UUID problemId;

        public Key() {}
        public Key(UUID decisionId, UUID problemId) {
            this.decisionId = decisionId;
            this.problemId = problemId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(decisionId, k.decisionId) && Objects.equals(problemId, k.problemId);
        }

        @Override
        public int hashCode() { return Objects.hash(decisionId, problemId); }
    }
}
