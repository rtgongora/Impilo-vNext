package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.RiskRuleEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RiskRuleRepository extends JpaRepository<RiskRuleEntity, Long> {

    List<RiskRuleEntity> findByTenantIdAndTemplateCodeAndStatus(UUID tenantId, String templateCode, String status);
}
