package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.DeliveryLegEntity;

import java.util.List;

@Repository
public interface DeliveryLegRepository extends JpaRepository<DeliveryLegEntity, String> {
    List<DeliveryLegEntity> findByPlanIdOrderBySequenceNumberAsc(String planId);
}

