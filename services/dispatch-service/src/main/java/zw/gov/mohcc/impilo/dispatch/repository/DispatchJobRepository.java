package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchJobEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface DispatchJobRepository extends JpaRepository<DispatchJobEntity, UUID> {

    @Query("SELECT j FROM DispatchJobEntity j WHERE j.tenantId = :tenantId"
            + " AND (:facilityRef IS NULL OR j.facilityRef = :facilityRef)"
            + " AND (:status IS NULL OR j.status = :status)")
    Page<DispatchJobEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                         @Param("facilityRef") String facilityRef,
                                         @Param("status") String status,
                                         Pageable pageable);

    @Query("SELECT j FROM DispatchJobEntity j WHERE j.createdAt <= :asOf")
    Page<DispatchJobEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf,
                                             Pageable pageable);
}
