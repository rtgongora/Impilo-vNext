package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.FundingSourceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FundingSourceRepository extends JpaRepository<FundingSourceEntity, Long> {

    Optional<FundingSourceEntity> findByFundingSourceIdAndTenantId(UUID fundingSourceId, UUID tenantId);

    Optional<FundingSourceEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<FundingSourceEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
