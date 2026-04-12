package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardUpdateQueueEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardUpdateQueueRepository extends JpaRepository<CardUpdateQueueEntity, Long> {

    List<CardUpdateQueueEntity> findByCardIdAndStatusOrderByPriorityAscCreatedAtAsc(UUID cardId, String status);

    List<CardUpdateQueueEntity> findByCardIdAndStatus(UUID cardId, String status);

    Optional<CardUpdateQueueEntity> findByQueueId(UUID queueId);
}
