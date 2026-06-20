package zw.gov.mohcc.impilo.scheduling.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.SlotReservationEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotReservationRepository extends JpaRepository<SlotReservationEntity, Long> {

    List<SlotReservationEntity> findByTenantIdAndResourceIdAndSlotDate(UUID tenantId, String resourceId, LocalDate slotDate);

    Optional<SlotReservationEntity> findByTenantIdAndResourceIdAndSlotDateAndStartTime(
            UUID tenantId, String resourceId, LocalDate slotDate, LocalTime startTime);
}
