package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderStatusHistoryRepository extends JpaRepository<ProviderStatusHistoryEntity, Long> {

    List<ProviderStatusHistoryEntity> findByProviderIdOrderByChangedAtDesc(Long providerId);

    List<ProviderStatusHistoryEntity> findByTenantIdOrderByChangedAtDesc(UUID tenantId);
}