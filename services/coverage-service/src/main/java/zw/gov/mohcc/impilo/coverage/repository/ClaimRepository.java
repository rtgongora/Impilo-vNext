package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimEntity, UUID> {

    List<ClaimEntity> findByTenantIdAndCoverageId(UUID tenantId, UUID coverageId);

    List<ClaimEntity> findByTenantIdAndCoverageIdIn(UUID tenantId, Collection<UUID> coverageIds);

    Optional<ClaimEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
