package zw.gov.mohcc.impilo.surv.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyCounterRepository extends JpaRepository<DailyCounterEntity, Long> {
    List<DailyCounterEntity> findByTenantId(UUID tenantId);
    List<DailyCounterEntity> findByTenantIdAndCountDateBetween(UUID tenantId, LocalDate from, LocalDate to);
    List<DailyCounterEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);
    Optional<DailyCounterEntity> findByTenantIdAndFacilityIdAndSyndromeCodeAndCountDate(
            UUID tenantId, UUID facilityId, String syndromeCode, LocalDate countDate);
}
