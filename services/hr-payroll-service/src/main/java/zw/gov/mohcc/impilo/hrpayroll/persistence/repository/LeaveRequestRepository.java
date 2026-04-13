package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.LeaveRequestEntity;

import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequestEntity, UUID> {
    List<LeaveRequestEntity> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);
}
