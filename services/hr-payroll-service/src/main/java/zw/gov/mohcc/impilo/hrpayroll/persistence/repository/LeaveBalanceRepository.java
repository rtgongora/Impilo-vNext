package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.LeaveBalanceEntity;

import java.util.List;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalanceEntity, UUID> {
    List<LeaveBalanceEntity> findByEmployeeIdAndFiscalYear(UUID employeeId, int fiscalYear);
}
