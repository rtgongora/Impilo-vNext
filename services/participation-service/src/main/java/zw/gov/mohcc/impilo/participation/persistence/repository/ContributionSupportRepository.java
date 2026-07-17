package zw.gov.mohcc.impilo.participation.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionSupportEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContributionSupportRepository extends JpaRepository<ContributionSupportEntity, UUID> {

    Optional<ContributionSupportEntity> findByContributionIdAndSupporterKey(UUID contributionId, String supporterKey);

    boolean existsByContributionIdAndSupporterKey(UUID contributionId, String supporterKey);

    long countByContributionId(UUID contributionId);
}
