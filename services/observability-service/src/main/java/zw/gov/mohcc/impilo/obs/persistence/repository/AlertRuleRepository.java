package zw.gov.mohcc.impilo.obs.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.obs.persistence.entity.AlertRuleEntity;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, Long> {

    List<AlertRuleEntity> findByTenantId(UUID tenantId);
}
