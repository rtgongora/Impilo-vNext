package zw.gov.mohcc.impilo.gl.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.gl.persistence.entity.BudgetLineEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLineEntity, UUID> {

    List<BudgetLineEntity> findByTenantIdAndFiscalYearOrderByAccountId(UUID tenantId, int fiscalYear);

    Optional<BudgetLineEntity> findByTenantIdAndAccountIdAndFiscalYear(UUID tenantId, UUID accountId, int fiscalYear);
}
