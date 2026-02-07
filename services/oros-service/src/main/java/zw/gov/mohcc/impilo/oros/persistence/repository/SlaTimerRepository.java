package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.persistence.entity.SlaTimerEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SlaTimerRepository extends JpaRepository<SlaTimerEntity, UUID> {

    List<SlaTimerEntity> findByOrderId(String orderId);

    /**
     * Finds all SLA timers that have not been breached but whose target time has passed.
     *
     * @param cutoff the cutoff time (timers with targetAt before this are overdue)
     * @return list of breachable timer entities
     */
    List<SlaTimerEntity> findByBreachedFalseAndTargetAtBefore(OffsetDateTime cutoff);
}
