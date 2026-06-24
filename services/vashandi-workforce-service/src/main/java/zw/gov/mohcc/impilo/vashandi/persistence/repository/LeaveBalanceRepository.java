package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveBalanceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalanceEntity, UUID> {

    List<LeaveBalanceEntity> findByTenantIdAndWorkforceProfileIdAndFiscalYear(
            UUID tenantId, UUID workforceProfileId, int fiscalYear);

    Optional<LeaveBalanceEntity> findByTenantIdAndWorkforceProfileIdAndLeaveTypeAndFiscalYear(
            UUID tenantId, UUID workforceProfileId, String leaveType, int fiscalYear);
}
