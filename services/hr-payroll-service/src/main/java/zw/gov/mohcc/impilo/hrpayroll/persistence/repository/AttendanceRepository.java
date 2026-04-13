package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.AttendanceRecordEntity;

import java.util.List;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceRecordEntity, UUID> {
    List<AttendanceRecordEntity> findByEmployeeIdOrderByShiftDateDesc(UUID employeeId);
}
