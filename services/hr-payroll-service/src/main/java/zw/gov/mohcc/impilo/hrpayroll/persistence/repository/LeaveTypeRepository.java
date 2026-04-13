package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.LeaveTypeEntity;

import java.util.List;
import java.util.UUID;

public interface LeaveTypeRepository extends JpaRepository<LeaveTypeEntity, UUID> {
    List<LeaveTypeEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
