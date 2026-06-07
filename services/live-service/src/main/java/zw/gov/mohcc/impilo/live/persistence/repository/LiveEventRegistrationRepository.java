package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRegistrationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventRegistrationRepository extends JpaRepository<LiveEventRegistrationEntity, UUID> {

    List<LiveEventRegistrationEntity> findByEventIdAndRegistrationStatus(UUID eventId, String status);

    Optional<LiveEventRegistrationEntity> findByEventIdAndParticipantId(UUID eventId, String participantId);

    List<LiveEventRegistrationEntity> findByParticipantIdAndRegistrationStatus(String participantId, String status);

    List<LiveEventRegistrationEntity> findByParticipantIdAndSavedTrue(String participantId);

    long countByEventIdAndRegistrationStatus(UUID eventId, String status);
}
