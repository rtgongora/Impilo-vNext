package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AlertEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    @Query("SELECT a FROM AlertEntity a WHERE a.tenantId = :tenantId " +
           "AND a.status = 'OPEN' ORDER BY a.createdAt DESC")
    Page<AlertEntity> findOpenAlerts(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT a FROM AlertEntity a WHERE a.facility.id = :facilityId AND a.status = :status ORDER BY a.createdAt DESC")
    Page<AlertEntity> findByFacilityIdAndStatusOrderByCreatedAtDesc(
            @Param("facilityId") Long facilityId, @Param("status") String status, Pageable pageable);

    Page<AlertEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT a FROM AlertEntity a WHERE a.facility.id = :facilityId AND a.alertType = :alertType AND a.status = :status")
    Optional<AlertEntity> findByFacilityIdAndAlertTypeAndStatus(
            @Param("facilityId") Long facilityId, @Param("alertType") String alertType, @Param("status") String status);
}
