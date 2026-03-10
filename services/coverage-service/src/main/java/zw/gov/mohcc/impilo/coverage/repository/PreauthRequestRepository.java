package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.PreauthRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreauthRequestRepository extends JpaRepository<PreauthRequestEntity, UUID> {

    List<PreauthRequestEntity> findByTenantIdAndCoverageId(UUID tenantId, UUID coverageId);

    Optional<PreauthRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
