package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AppointmentEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {

    Optional<AppointmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            SELECT a FROM AppointmentEntity a
            WHERE a.tenantId = :tenantId
              AND (:patientId IS NULL OR a.patientId = :patientId)
              AND (:facilityId IS NULL OR a.facilityId = :facilityId)
              AND (:status IS NULL OR a.status = :status)
            ORDER BY a.scheduledAt ASC
            """)
    Page<AppointmentEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("patientId") String patientId,
            @Param("facilityId") Long facilityId,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
            SELECT a FROM AppointmentEntity a
            WHERE a.tenantId = :tenantId
              AND a.patientCpid = :cpid
              AND (:status IS NULL OR a.status = :status)
            ORDER BY a.scheduledAt ASC
            """)
    Page<AppointmentEntity> findByCitizen(
            @Param("tenantId") UUID tenantId,
            @Param("cpid") String cpid,
            @Param("status") String status,
            Pageable pageable);
}
