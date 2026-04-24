package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargeRecordEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChargeRecordRepository extends JpaRepository<ChargeRecordEntity, String> {

    List<ChargeRecordEntity> findByTenantIdAndBillIdOrderByCreatedAtDesc(UUID tenantId, String billId);

    boolean existsByTenantIdAndSourceTypeAndSourceRef(UUID tenantId, String sourceType, String sourceRef);
}
