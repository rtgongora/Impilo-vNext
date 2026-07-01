package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetApprovalEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetApprovalRepository extends JpaRepository<BudgetApprovalEntity, Long> {

    List<BudgetApprovalEntity> findByBudgetIdAndTenantIdOrderByDecidedAtDesc(UUID budgetId, UUID tenantId);
}
