package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventFeedbackEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventFeedbackRepository extends JpaRepository<LiveEventFeedbackEntity, UUID> {

    List<LiveEventFeedbackEntity> findByEventId(UUID eventId);

    Optional<LiveEventFeedbackEntity> findByEventIdAndParticipantId(UUID eventId, String participantId);
}
