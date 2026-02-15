package zw.gov.mohcc.impilo.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rules.domain.RuleEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleRepository extends JpaRepository<RuleEntity, String> {

    List<RuleEntity> findByTenantIdAndEnabledTrue(String tenantId);

    List<RuleEntity> findByTenantId(String tenantId);

    Optional<RuleEntity> findByKeyAndTenantId(String key, String tenantId);
}
