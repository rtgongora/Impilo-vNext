package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilSpecialtyRecordEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderCouncilSpecialtyRecordRepository extends JpaRepository<ProviderCouncilSpecialtyRecordEntity, Long> {

    List<ProviderCouncilSpecialtyRecordEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);
}
