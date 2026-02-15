package zw.gov.mohcc.impilo.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rules.domain.RuleVersionEntity;

import java.util.Optional;

@Repository
public interface RuleVersionRepository extends JpaRepository<RuleVersionEntity, String> {

    Optional<RuleVersionEntity> findByRuleIdAndVersion(String ruleId, int version);

    Optional<RuleVersionEntity> findTopByRuleIdOrderByVersionDesc(String ruleId);
}
