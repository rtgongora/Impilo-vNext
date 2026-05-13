package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayEntity;

public interface FundoPathwayRepository extends JpaRepository<FundoPathwayEntity, UUID> {
    Optional<FundoPathwayEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<FundoPathwayEntity> findByTenantIdAndCode(UUID tenantId, String code);
    List<FundoPathwayEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
