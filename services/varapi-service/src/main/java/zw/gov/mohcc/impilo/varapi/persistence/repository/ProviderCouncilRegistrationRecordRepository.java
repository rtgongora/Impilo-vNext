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

    // Council/EC-number resolver (L3): resolve a council registration number to
    // its provider anchor, scoped by council code when supplied. The number
    // resolves a profile but never authenticates (LOGIN-PROVIDERID-DENY).
    Optional<ProviderCouncilRegistrationRecordEntity>
        findFirstByTenantIdAndCouncil_CouncilCodeIgnoreCaseAndRegistrationNumberIgnoreCase(
            UUID tenantId, String councilCode, String registrationNumber);

    Optional<ProviderCouncilRegistrationRecordEntity>
        findFirstByTenantIdAndRegistrationNumberIgnoreCase(UUID tenantId, String registrationNumber);
}
