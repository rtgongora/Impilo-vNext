package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.HouseholdEntity;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdRepository extends JpaRepository<HouseholdEntity, UUID> {

    Page<HouseholdEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, UUID facilityId, Pageable pageable);

    Optional<HouseholdEntity> findByTenantIdAndOfflineId(UUID tenantId, String offlineId);
}
