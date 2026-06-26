package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderClaimTokenEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderClaimTokenRepository extends JpaRepository<ProviderClaimTokenEntity, Long> {

    Optional<ProviderClaimTokenEntity> findByTenantIdAndTokenHash(UUID tenantId, String tokenHash);

    List<ProviderClaimTokenEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);
}
