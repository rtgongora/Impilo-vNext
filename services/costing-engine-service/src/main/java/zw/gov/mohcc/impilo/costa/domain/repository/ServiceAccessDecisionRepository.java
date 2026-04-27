package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.costa.domain.entity.ServiceAccessDecisionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAccessDecisionRepository extends JpaRepository<ServiceAccessDecisionEntity, UUID> {

    List<ServiceAccessDecisionEntity> findTop50ByTenantIdOrderByDecisionTimestampDesc(UUID tenantId);

    List<ServiceAccessDecisionEntity> findTop50ByTenantIdAndEncounterIdOrderByDecisionTimestampDesc(
            UUID tenantId, String encounterId);

    Optional<ServiceAccessDecisionEntity> findByServiceAccessDecisionIdAndTenantId(UUID id, UUID tenantId);
}
