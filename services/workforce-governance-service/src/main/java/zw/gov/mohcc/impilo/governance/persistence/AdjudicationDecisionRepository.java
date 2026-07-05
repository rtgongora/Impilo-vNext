package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the APPEND-ONLY {@link AdjudicationDecisionEntity}. Only
 * insert and read operations are used by the service layer — never update or
 * delete (migration V008 additionally enforces immutability via trigger).
 */
@Repository
public interface AdjudicationDecisionRepository extends JpaRepository<AdjudicationDecisionEntity, UUID> {

    /**
     * Full decision history for a subject, newest-effective first (createdAt
     * breaks ties so the most recently recorded row wins). The first row
     * satisfying the effective/expiry window is the latest-effective decision.
     */
    List<AdjudicationDecisionEntity>
    findByTenantIdAndSubjectTypeAndSubjectRefOrderByEffectiveAtDescCreatedAtDesc(
            UUID tenantId, String subjectType, String subjectRef);

    List<AdjudicationDecisionEntity> findByTenantIdAndWorkflowInstanceIdOrderByCreatedAtDesc(
            UUID tenantId, UUID workflowInstanceId);
}
