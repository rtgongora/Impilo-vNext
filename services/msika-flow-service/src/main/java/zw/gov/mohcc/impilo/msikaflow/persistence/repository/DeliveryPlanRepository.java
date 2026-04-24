package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.DeliveryPlanEntity;

import java.util.List;

@Repository
public interface DeliveryPlanRepository extends JpaRepository<DeliveryPlanEntity, String> {
    List<DeliveryPlanEntity> findByFulfillmentIdOrderByCreatedAtAsc(String fulfillmentId);
}

