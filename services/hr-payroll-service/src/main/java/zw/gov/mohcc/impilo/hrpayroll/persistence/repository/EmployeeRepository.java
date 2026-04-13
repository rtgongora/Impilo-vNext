package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.EmployeeEntity;

import java.util.List;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID> {
    List<EmployeeEntity> findByTenantIdOrderByStaffNumberAsc(UUID tenantId);
}
