package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeParticipantEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipantEntity, Long> {

    Optional<ChallengeParticipantEntity> findByChallengeIdAndPersonCpid(UUID challengeId, String personCpid);
}
