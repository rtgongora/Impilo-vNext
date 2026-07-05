package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventStageRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventStageRequestRepository extends JpaRepository<LiveEventStageRequestEntity, UUID> {

    List<LiveEventStageRequestEntity> findByEventIdOrderByRequestedAtAsc(UUID eventId);

    List<LiveEventStageRequestEntity> findByEventIdAndStatusOrderByRequestedAtAsc(UUID eventId, String status);

    /** The participant's open (REQUESTED/APPROVED) request, if any. */
    Optional<LiveEventStageRequestEntity> findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
            UUID eventId, String participantId, List<String> statuses);

    Optional<LiveEventStageRequestEntity> findFirstByEventIdAndParticipantIdOrderByRequestedAtDesc(
            UUID eventId, String participantId);
}
