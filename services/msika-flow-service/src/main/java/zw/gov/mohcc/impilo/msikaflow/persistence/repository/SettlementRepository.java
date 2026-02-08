package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SettlementEntity;

import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<SettlementEntity, String> {
    Optional<SettlementEntity> findByMushexPaymentIntentId(String mushexPaymentIntentId);
    Optional<SettlementEntity> findByOrderId(String orderId);
}
