package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilStandingHistoryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderCouncilStandingHistoryRepository extends JpaRepository<ProviderCouncilStandingHistoryEntity, Long> {

    List<ProviderCouncilStandingHistoryEntity> findByTenantIdAndProvider_IdOrderByChangedAtDesc(
            UUID tenantId, Long providerId);
}
