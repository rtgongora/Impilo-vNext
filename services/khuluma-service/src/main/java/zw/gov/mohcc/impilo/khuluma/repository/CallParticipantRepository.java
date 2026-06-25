package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.CallParticipantEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallParticipantRepository extends JpaRepository<CallParticipantEntity, UUID> {

    List<CallParticipantEntity> findByCallId(UUID callId);

    Optional<CallParticipantEntity> findByCallIdAndActorId(UUID callId, String actorId);
}
