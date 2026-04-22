package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOrderEntity;

import java.util.List;

@Repository
public interface FulfillmentOrderRepository extends JpaRepository<FulfillmentOrderEntity, String> {
    List<FulfillmentOrderEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);
}

