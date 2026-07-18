package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.RecordClaimChallengeEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecordClaimChallengeRepository extends JpaRepository<RecordClaimChallengeEntity, Long> {
    Optional<RecordClaimChallengeEntity> findByChallengeId(UUID challengeId);
}
