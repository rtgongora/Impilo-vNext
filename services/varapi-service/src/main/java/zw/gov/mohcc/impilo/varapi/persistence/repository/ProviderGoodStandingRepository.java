package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderGoodStandingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderGoodStandingRepository extends JpaRepository<ProviderGoodStandingEntity, Long> {
    List<ProviderGoodStandingEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);
    Optional<ProviderGoodStandingEntity> findByTenantIdAndProviderIdAndCouncilId(UUID tenantId, Long providerId, Long councilId);
}
