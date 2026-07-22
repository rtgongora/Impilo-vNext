package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CaseDocketAssignmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseDocketAssignmentRepository extends JpaRepository<CaseDocketAssignmentEntity, Long> {
    List<CaseDocketAssignmentEntity> findByTenantIdAndCaseId(UUID tenantId, Long caseId);
    List<CaseDocketAssignmentEntity> findByTenantIdAndCommitteeMemberHealthIdAndStatus(UUID tenantId, String memberHealthId, String status);
    boolean existsByTenantIdAndCaseIdAndCommitteeMemberHealthIdAndStatus(UUID tenantId, Long caseId, String memberHealthId, String status);
}
// member-scoped docket queries added below via a separate interface method
