package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.nhume.domain.DriverCourierProfileEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverCourierProfileRepository extends JpaRepository<DriverCourierProfileEntity, UUID> {

    Optional<DriverCourierProfileEntity> findByTenantIdAndCourierCode(UUID tenantId, String courierCode);

    @Query("SELECT c FROM DriverCourierProfileEntity c WHERE c.tenantId = :tenantId"
            + " AND (:kind IS NULL OR c.courierKind = :kind)"
            + " AND (:status IS NULL OR c.currentStatus = :status)"
            + " AND c.active = TRUE"
            + " ORDER BY c.displayName ASC")
    Page<DriverCourierProfileEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                                   @Param("kind") String kind,
                                                   @Param("status") String status,
                                                   Pageable pageable);

    @Query("SELECT c FROM DriverCourierProfileEntity c WHERE c.tenantId = :tenantId"
            + " AND c.currentStatus = 'ON_DUTY' AND c.active = TRUE")
    List<DriverCourierProfileEntity> findOnDuty(@Param("tenantId") UUID tenantId);

    long countByTenantIdAndCurrentStatus(UUID tenantId, String currentStatus);

    long countByTenantIdAndActiveTrue(UUID tenantId);
}
