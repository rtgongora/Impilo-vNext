package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyBalanceEntity;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubsidyBalanceRepository extends JpaRepository<SubsidyBalanceEntity, UUID> {

    Optional<SubsidyBalanceEntity> findByEnrolmentIdAndPeriodYear(UUID enrolmentId, int periodYear);

    /**
     * Atomic, concurrency-safe drawdown (H1). Increments {@code consumed_amount} by
     * {@code amount} only when the new total stays at or under the cap (a null cap means
     * uncapped — always allowed). Returns the affected row count: 1 on success, 0 when the
     * drawdown would breach the cap. The database serialises the check-and-apply, so two
     * concurrent consume() calls cannot both pass an at-cap boundary (no lost update).
     *
     * <p>Boundary: exactly-at-cap is allowed ({@code <=}); strictly-over is rejected.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE cv_subsidy_balances
               SET consumed_amount = consumed_amount + :amount,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :id
               AND (cap_amount IS NULL OR consumed_amount + :amount <= cap_amount)
            """, nativeQuery = true)
    int applyDrawdown(@Param("id") UUID balanceId, @Param("amount") BigDecimal amount);
}
