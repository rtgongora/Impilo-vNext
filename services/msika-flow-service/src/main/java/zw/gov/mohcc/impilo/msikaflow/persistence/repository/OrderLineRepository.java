package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderLineEntity;

import java.util.List;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLineEntity, String> {
    List<OrderLineEntity> findByOrderId(String orderId);
}
