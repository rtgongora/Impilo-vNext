package zw.gov.mohcc.impilo.telemonitoring.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertRuleEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, UUID> {

    Optional<AlertRuleEntity> findByPlanId(UUID planId);

    Optional<AlertRuleEntity> findFirstByTenantIdAndProgrammeCodeAndPlanIdIsNull(UUID tenantId, String programmeCode);
}
