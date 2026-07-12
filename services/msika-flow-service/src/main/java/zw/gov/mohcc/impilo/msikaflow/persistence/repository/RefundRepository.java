package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.RefundEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, String> {
    List<RefundEntity> findByOrderId(String orderId);
    Optional<RefundEntity> findByMushexRefundId(String mushexRefundId);
}
