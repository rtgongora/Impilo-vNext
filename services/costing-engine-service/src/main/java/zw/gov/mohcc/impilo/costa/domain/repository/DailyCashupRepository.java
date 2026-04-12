package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.DailyCashupEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyCashupRepository extends JpaRepository<DailyCashupEntity, Long> {

    Optional<DailyCashupEntity> findByCashupId(UUID cashupId);

    Optional<DailyCashupEntity> findByTenantIdAndFacilityIdAndCashupDateAndCashierIdAndStatus(
            UUID tenantId, UUID facilityId, LocalDate cashupDate, String cashierId, String status);

    List<DailyCashupEntity> findByTenantIdAndFacilityIdAndCashupDateBetweenOrderByCashupDateDesc(
            UUID tenantId, UUID facilityId, LocalDate from, LocalDate to);

    List<DailyCashupEntity> findByTenantIdAndFacilityIdAndCashupDateOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId, LocalDate cashupDate);
}
