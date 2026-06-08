package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventChatMessageEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiveEventChatMessageRepository extends JpaRepository<LiveEventChatMessageEntity, UUID> {

    List<LiveEventChatMessageEntity> findByEventIdAndStatusOrderByCreatedAtAsc(UUID eventId, String status);

    List<LiveEventChatMessageEntity> findByEventIdOrderByCreatedAtAsc(UUID eventId);
}
