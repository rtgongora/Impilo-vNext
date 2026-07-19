package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.SchemeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchemeRepository extends JpaRepository<SchemeEntity, UUID> {
    List<SchemeEntity> findByTenantIdAndPayerRef(UUID tenantId, UUID payerRef);
    Optional<SchemeEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<SchemeEntity> findByTenantIdAndSchemeCode(UUID tenantId, String schemeCode);
}
