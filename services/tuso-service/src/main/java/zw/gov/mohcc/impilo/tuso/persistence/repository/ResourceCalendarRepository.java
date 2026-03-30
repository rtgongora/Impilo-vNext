package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceCalendarEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResourceCalendarRepository extends JpaRepository<ResourceCalendarEntity, Long> {

    List<ResourceCalendarEntity> findByResource_IdAndActiveTrue(UUID resourceId);

    List<ResourceCalendarEntity> findByResource_IdAndDayOfWeekAndActiveTrue(UUID resourceId, Integer dayOfWeek);
}
