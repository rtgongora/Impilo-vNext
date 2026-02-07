package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyDecisionLogEntity;

@Repository
public interface PolicyDecisionLogRepository extends JpaRepository<PolicyDecisionLogEntity, Long> {
}
