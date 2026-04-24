package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilProfileEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCouncilProfileRepository extends JpaRepository<ProviderCouncilProfileEntity, Long> {

    List<ProviderCouncilProfileEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);

    Optional<ProviderCouncilProfileEntity> findByIdAndTenantId(Long id, UUID tenantId);
}
