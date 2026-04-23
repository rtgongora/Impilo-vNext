package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ExternalProviderAccessProfileEntity;

import java.util.Optional;
import java.util.UUID;

public interface ExternalProviderAccessProfileRepository extends JpaRepository<ExternalProviderAccessProfileEntity, Long> {

    Optional<ExternalProviderAccessProfileEntity> findByTemporaryProviderPublicId(String temporaryProviderPublicId);

    Optional<ExternalProviderAccessProfileEntity> findByTenantIdAndVitoAccessGrantRef(UUID tenantId, String vitoAccessGrantRef);
}
