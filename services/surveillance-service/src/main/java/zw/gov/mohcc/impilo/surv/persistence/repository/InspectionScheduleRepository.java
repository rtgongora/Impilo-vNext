package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.InspectionScheduleEntity;

public interface InspectionScheduleRepository extends JpaRepository<InspectionScheduleEntity, Long> {
    List<InspectionScheduleEntity> findByTenantIdOrderByScheduledAtDesc(UUID tenantId);
}
