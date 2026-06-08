package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventPollResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventPollResponseRepository extends JpaRepository<LiveEventPollResponseEntity, UUID> {

    List<LiveEventPollResponseEntity> findByPollId(UUID pollId);

    Optional<LiveEventPollResponseEntity> findByPollIdAndParticipantId(UUID pollId, String participantId);

    long countByPollIdAndSelectedOption(UUID pollId, String selectedOption);
}
