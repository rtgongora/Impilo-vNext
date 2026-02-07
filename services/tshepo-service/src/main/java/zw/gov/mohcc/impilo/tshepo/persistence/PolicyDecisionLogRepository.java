package zw.gov.mohcc.impilo.tshepo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyDecisionLogRepository extends JpaRepository<PolicyDecisionLogEntity, Long> {
}
