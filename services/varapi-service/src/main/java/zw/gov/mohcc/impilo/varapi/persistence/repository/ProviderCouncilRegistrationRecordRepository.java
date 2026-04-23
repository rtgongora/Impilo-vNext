package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCouncilRegistrationRecordRepository
        extends JpaRepository<ProviderCouncilRegistrationRecordEntity, Long> {

    List<ProviderCouncilRegistrationRecordEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);

    Optional<ProviderCouncilRegistrationRecordEntity> findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(
            UUID tenantId, Long councilId, String registrationNumber);
}
