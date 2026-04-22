package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.HandoffEventEntity;

import java.util.List;

@Repository
public interface HandoffEventRepository extends JpaRepository<HandoffEventEntity, String> {
    List<HandoffEventEntity> findByFulfillmentIdOrderByOccurredAtDesc(String fulfillmentId);
    List<HandoffEventEntity> findByLegIdOrderByOccurredAtDesc(String legId);
}

