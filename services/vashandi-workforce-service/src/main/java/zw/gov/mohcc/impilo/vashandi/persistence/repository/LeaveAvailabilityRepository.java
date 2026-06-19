package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveAvailabilityEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveAvailabilityRepository extends JpaRepository<LeaveAvailabilityEntity, UUID> {

    List<LeaveAvailabilityEntity> findByTenantIdOrderByStartDateDesc(UUID tenantId);

    Optional<LeaveAvailabilityEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LeaveAvailabilityEntity> findByTenantIdAndWorkforceProfileIdOrderByStartDateDesc(
            UUID tenantId, UUID workforceProfileId);

    List<LeaveAvailabilityEntity> findByTenantIdAndWorkforceProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, UUID workforceProfileId, String status, LocalDate date, LocalDate sameDate);
}
