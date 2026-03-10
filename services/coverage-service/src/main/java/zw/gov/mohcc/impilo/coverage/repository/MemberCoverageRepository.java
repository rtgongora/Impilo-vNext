package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberCoverageRepository extends JpaRepository<MemberCoverageEntity, UUID> {

    List<MemberCoverageEntity> findByTenantIdAndClientId(UUID tenantId, String clientId);

    List<MemberCoverageEntity> findByTenantIdAndClientIdAndStatus(UUID tenantId, String clientId, String status);

    Optional<MemberCoverageEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
