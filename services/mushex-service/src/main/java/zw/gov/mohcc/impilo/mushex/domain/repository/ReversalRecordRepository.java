package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReversalRecordEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReversalRecordRepository extends JpaRepository<ReversalRecordEntity, String> {

    List<ReversalRecordEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByTenantIdAndReversalCode(UUID tenantId, String reversalCode);
}
