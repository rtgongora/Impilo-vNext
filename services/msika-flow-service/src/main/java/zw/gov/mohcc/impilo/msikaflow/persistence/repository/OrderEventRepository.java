package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEventEntity;

import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEventEntity, String> {
    List<OrderEventEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);
}
