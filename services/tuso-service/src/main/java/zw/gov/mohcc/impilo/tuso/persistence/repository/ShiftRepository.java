package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ShiftEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRepository extends JpaRepository<ShiftEntity, UUID> {

    List<ShiftEntity> findByFacilityIdAndStatus(Long facilityId, String status);

    @Query("SELECT s FROM ShiftEntity s WHERE s.tenantId = :tenantId " +
           "AND s.providerId = :providerId AND s.status = 'ACTIVE'")
    Optional<ShiftEntity> findActiveShift(
            @Param("tenantId") UUID tenantId,
            @Param("providerId") String providerId);

    Page<ShiftEntity> findByFacilityIdOrderByStartedAtDesc(Long facilityId, Pageable pageable);

    Page<ShiftEntity> findByTenantIdAndProviderIdOrderByStartedAtDesc(
            UUID tenantId, String providerId, Pageable pageable);
}
