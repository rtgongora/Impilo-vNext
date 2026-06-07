package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventAttendanceRepository extends JpaRepository<LiveEventAttendanceEntity, UUID> {

    List<LiveEventAttendanceEntity> findByEventId(UUID eventId);

    Optional<LiveEventAttendanceEntity> findByEventIdAndParticipantId(UUID eventId, String participantId);

    List<LiveEventAttendanceEntity> findByParticipantId(String participantId);

    long countByEventIdAndEligibleForCpdTrue(UUID eventId);
}
