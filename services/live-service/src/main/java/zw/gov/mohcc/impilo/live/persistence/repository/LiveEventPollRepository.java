package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventPollEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiveEventPollRepository extends JpaRepository<LiveEventPollEntity, UUID> {

    List<LiveEventPollEntity> findByEventIdAndStatus(UUID eventId, String status);

    List<LiveEventPollEntity> findByEventIdOrderByCreatedAtDesc(UUID eventId);
}
