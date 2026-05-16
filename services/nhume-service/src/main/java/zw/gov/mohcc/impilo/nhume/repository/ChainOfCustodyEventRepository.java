package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.ChainOfCustodyEventEntity;

import java.util.List;
import java.util.UUID;

public interface ChainOfCustodyEventRepository extends JpaRepository<ChainOfCustodyEventEntity, UUID> {
    List<ChainOfCustodyEventEntity> findByDeliveryIdOrderBySequenceNoAsc(UUID deliveryId);
}
