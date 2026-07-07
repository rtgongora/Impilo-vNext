package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChallengeRepository extends JpaRepository<ChallengeEntity, Long> {

    List<ChallengeEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ChallengeEntity> findByChallengeIdAndTenantId(UUID challengeId, UUID tenantId);

    List<ChallengeEntity> findByTenantIdAndGroupIdOrderByCreatedAtDesc(UUID tenantId, UUID groupId);

    List<ChallengeEntity> findByTenantIdAndProgrammeIdOrderByCreatedAtDesc(UUID tenantId, UUID programmeId);

    List<ChallengeEntity> findByTenantIdAndCampaignFlagTrueOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndProgrammeId(UUID tenantId, UUID programmeId);
}
