package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.PayrollRunEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRunEntity, UUID> {
    Optional<PayrollRunEntity> findByTenantIdAndPeriodYearAndPeriodMonth(UUID tenantId, int year, int month);

    List<PayrollRunEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
