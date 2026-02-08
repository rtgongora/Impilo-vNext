package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutBatchEntity;

import java.util.List;

@Repository
public interface PayoutBatchRepository extends JpaRepository<PayoutBatchEntity, String> {

    List<PayoutBatchEntity> findBySettlementId(String settlementId);
}
