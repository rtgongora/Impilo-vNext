package zw.gov.mohcc.impilo.gl.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.gl.persistence.entity.FiscalPeriodEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FiscalPeriodRepository extends JpaRepository<FiscalPeriodEntity, UUID> {

    @Query("select p from FiscalPeriodEntity p where p.tenantId = :tenantId and p.status = 'OPEN' order by p.fiscalYear, p.periodNumber")
    List<FiscalPeriodEntity> findOpenPeriods(@Param("tenantId") UUID tenantId);

    @Query("select p from FiscalPeriodEntity p where p.tenantId = :tenantId and :d between p.startDate and p.endDate")
    Optional<FiscalPeriodEntity> findContainingDate(@Param("tenantId") UUID tenantId, @Param("d") LocalDate d);

    List<FiscalPeriodEntity> findByTenantIdAndFiscalYearOrderByPeriodNumberAsc(UUID tenantId, int fiscalYear);
}
