package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceContributionSeenEntity;

import java.util.List;
import java.util.UUID;

public interface AssistanceContributionSeenRepository
        extends JpaRepository<AssistanceContributionSeenEntity, UUID> {

    List<AssistanceContributionSeenEntity> findByAssistanceIdOrderByRecordedAtDesc(UUID assistanceId);
}
