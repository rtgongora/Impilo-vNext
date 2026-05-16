package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.PhAlertRuleEntity;

public interface PhAlertRuleRepository extends JpaRepository<PhAlertRuleEntity, Long> {
    List<PhAlertRuleEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    Optional<PhAlertRuleEntity> findByIdAndTenantId(Long id, UUID tenantId);
    long countByTenantIdAndActiveTrue(UUID tenantId);
}
