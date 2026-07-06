package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilAdapterRegistrationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouncilAdapterRegistrationRepository
        extends JpaRepository<CouncilAdapterRegistrationEntity, Long> {

    /** The single enabled live endpoint for a tenant + council, if any. */
    Optional<CouncilAdapterRegistrationEntity> findFirstByTenantIdAndCouncilCodeIgnoreCaseAndEnabledTrue(
            UUID tenantId, String councilCode);

    List<CouncilAdapterRegistrationEntity> findByTenantId(UUID tenantId);
}
