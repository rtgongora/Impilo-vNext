package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.ShadowDecisionLogEntity;

/** Measurement store only. No enforcement path reads this. */
@Repository
public interface ShadowDecisionLogRepository extends JpaRepository<ShadowDecisionLogEntity, Long> {
}
