package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.ChainOfCustodyEventEntity;

import java.util.List;
import java.util.UUID;

public interface ChainOfCustodyEventRepository extends JpaRepository<ChainOfCustodyEventEntity, Long> {
    List<ChainOfCustodyEventEntity> findByDeliveryIdOrderByCreatedAtAsc(UUID deliveryId);
}
