package zw.gov.mohcc.impilo.connectorfhir.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.connectorfhir.domain.RelayDestinationEntity;

import java.util.List;
import java.util.UUID;

public interface RelayDestinationRepository extends JpaRepository<RelayDestinationEntity, UUID> {
    List<RelayDestinationEntity> findByTenantIdAndEnabledTrue(UUID tenantId);
}
