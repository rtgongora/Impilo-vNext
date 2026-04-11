package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ReminderEntity;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {

    List<ReminderEntity> findByTenantIdAndActorIdAndStatusOrderByDueDateAsc(
            String tenantId, String actorId, String status);
}
