package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.FinancialPeriodEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriodEntity, Long> {

    Optional<FinancialPeriodEntity> findByTenantIdAndPeriodTypeAndPeriodYearAndPeriodMonth(
            UUID tenantId, String periodType, int periodYear, Integer periodMonth);

    Optional<FinancialPeriodEntity> findByPeriodId(UUID periodId);
}
