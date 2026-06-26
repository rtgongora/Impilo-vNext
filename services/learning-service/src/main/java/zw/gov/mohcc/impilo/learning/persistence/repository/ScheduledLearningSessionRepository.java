package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;

public interface ScheduledLearningSessionRepository
        extends JpaRepository<ScheduledLearningSessionEntity, UUID> {

    Optional<ScheduledLearningSessionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ScheduledLearningSessionEntity> findByTenantIdOrderByStartsAtDesc(UUID tenantId, Pageable pageable);

    List<ScheduledLearningSessionEntity> findByTenantIdAndCohortIdOrderByStartsAtAsc(UUID tenantId, UUID cohortId);
}
