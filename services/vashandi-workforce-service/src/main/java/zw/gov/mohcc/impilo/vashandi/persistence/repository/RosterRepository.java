package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.RosterEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RosterRepository extends JpaRepository<RosterEntity, UUID> {

    List<RosterEntity> findByTenantIdOrderByPeriodStartDesc(UUID tenantId);

    Optional<RosterEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<RosterEntity> findByTenantIdAndFacilityIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            UUID tenantId, UUID facilityId, LocalDate periodEnd, LocalDate periodStart);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
