package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBadgeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderBadgeRepository extends JpaRepository<ProviderBadgeEntity, Long> {

    Optional<ProviderBadgeEntity> findByBadgeSerial(String badgeSerial);

    List<ProviderBadgeEntity> findByTenantIdAndProviderIdOrderByIssuedAtDesc(UUID tenantId, Long providerId);
}
