package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.PreventiveReminderEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreventiveReminderRepository extends JpaRepository<PreventiveReminderEntity, Long> {

    Optional<PreventiveReminderEntity> findByReminderIdAndTenantId(UUID reminderId, UUID tenantId);

    List<PreventiveReminderEntity> findByTenantIdAndPersonCpidOrderByDueAtAsc(UUID tenantId, String personCpid);

    /** Reminders whose due time has passed and are still awaiting delivery. */
    @Query("""
            SELECT r FROM PreventiveReminderEntity r
            WHERE r.status IN ('SCHEDULED', 'DUE') AND r.dueAt <= :now
            ORDER BY r.dueAt ASC
            """)
    List<PreventiveReminderEntity> findDue(@Param("now") OffsetDateTime now, Pageable pageable);
}
