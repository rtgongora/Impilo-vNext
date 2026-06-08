package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonationDriveAttendanceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonationDriveAttendanceRepository extends JpaRepository<DonationDriveAttendanceEntity, Long> {
    Optional<DonationDriveAttendanceEntity> findByAttendanceIdAndTenantId(UUID attendanceId, UUID tenantId);
    List<DonationDriveAttendanceEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
