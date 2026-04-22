package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JurisdictionRepository extends JpaRepository<JurisdictionEntity, UUID> {

    Optional<JurisdictionEntity> findByTenantIdAndJurisdictionCode(UUID tenantId, String jurisdictionCode);

    List<JurisdictionEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
