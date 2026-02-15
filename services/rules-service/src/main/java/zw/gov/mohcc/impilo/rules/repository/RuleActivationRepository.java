package zw.gov.mohcc.impilo.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rules.domain.RuleActivationEntity;

import java.util.Optional;

@Repository
public interface RuleActivationRepository extends JpaRepository<RuleActivationEntity, String> {

    Optional<RuleActivationEntity> findByRuleIdAndActiveToIsNull(String ruleId);
}
