package zw.gov.mohcc.impilo.participation.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContributionEventRepository extends JpaRepository<ContributionEventEntity, UUID> {

    List<ContributionEventEntity> findByContributionIdOrderByOccurredAtAsc(UUID contributionId);
}
