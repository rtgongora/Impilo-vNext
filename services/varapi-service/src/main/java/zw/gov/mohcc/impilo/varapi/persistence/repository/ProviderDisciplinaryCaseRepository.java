package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderDisciplinaryCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderDisciplinaryCaseRepository extends JpaRepository<ProviderDisciplinaryCaseEntity, Long> {

    Optional<ProviderDisciplinaryCaseEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<ProviderDisciplinaryCaseEntity> findByProviderId(Long providerId);

    List<ProviderDisciplinaryCaseEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<ProviderDisciplinaryCaseEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderDisciplinaryCaseEntity> findByTenantIdAndTriggerType(UUID tenantId, String triggerType);
}