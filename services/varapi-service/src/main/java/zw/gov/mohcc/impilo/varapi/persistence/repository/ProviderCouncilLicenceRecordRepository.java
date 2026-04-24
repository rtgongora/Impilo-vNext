package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilLicenceRecordEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderCouncilLicenceRecordRepository extends JpaRepository<ProviderCouncilLicenceRecordEntity, Long> {

    List<ProviderCouncilLicenceRecordEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);
}
