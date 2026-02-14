package zw.gov.mohcc.impilo.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rules.domain.DecisionLogEntity;

@Repository
public interface DecisionLogRepository extends JpaRepository<DecisionLogEntity, String> {
}
