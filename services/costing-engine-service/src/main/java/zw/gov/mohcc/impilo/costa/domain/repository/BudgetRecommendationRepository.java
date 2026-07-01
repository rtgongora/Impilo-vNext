package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetRecommendationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRecommendationRepository extends JpaRepository<BudgetRecommendationEntity, Long> {
    Optional<BudgetRecommendationEntity> findByRecommendationIdAndTenantId(UUID recommendationId, UUID tenantId);
    List<BudgetRecommendationEntity> findByBudgetIdAndTenantIdOrderByCreatedAtDesc(UUID budgetId, UUID tenantId);
    Optional<BudgetRecommendationEntity> findFirstByTenantIdAndBudgetIdAndRuleCodeAndStatus(
            UUID tenantId, UUID budgetId, String ruleCode, String status);
}
