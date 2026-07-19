package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.BenefitAccumulatorEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenefitAccumulatorRepository extends JpaRepository<BenefitAccumulatorEntity, UUID> {

    Optional<BenefitAccumulatorEntity> findByMemberIdAndBenefitCodeAndPeriodKey(
            UUID memberId, String benefitCode, String periodKey);

    List<BenefitAccumulatorEntity> findByTenantIdAndMemberId(UUID tenantId, UUID memberId);

    /**
     * Atomic reserve (H1): move {@code amount} into reserved_amount only while it stays within
     * the limit (limit - used - reserved >= amount). Null limit = unlimited. Returns 1 on
     * success, 0 when the reservation would breach the remaining benefit.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE cv_benefit_accumulators
               SET reserved_amount = reserved_amount + :amount,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :id
               AND (limit_amount IS NULL OR used_amount + reserved_amount + :amount <= limit_amount)
            """, nativeQuery = true)
    int applyReserve(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * Atomic consume: increment used_amount, decrement reserved_amount by the same amount
     * (moves a prior reservation into actual usage). Guards reserved_amount from going below
     * zero. Returns 1 on success, 0 when there is insufficient reservation to convert.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE cv_benefit_accumulators
               SET used_amount = used_amount + :amount,
                   reserved_amount = reserved_amount - :amount,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :id
               AND reserved_amount >= :amount
            """, nativeQuery = true)
    int applyConsumeReserved(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * Atomic direct consume (no prior reservation): increment used_amount within the limit.
     * Returns 1 on success, 0 when it would breach the remaining benefit.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE cv_benefit_accumulators
               SET used_amount = used_amount + :amount,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :id
               AND (limit_amount IS NULL OR used_amount + reserved_amount + :amount <= limit_amount)
            """, nativeQuery = true)
    int applyConsumeDirect(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * Atomic release: return {@code amount} from reserved_amount (e.g. authorisation expired or
     * claim denied), never below zero.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE cv_benefit_accumulators
               SET reserved_amount = reserved_amount - :amount,
                   version = version + 1,
                   updated_at = now()
             WHERE id = :id
               AND reserved_amount >= :amount
            """, nativeQuery = true)
    int applyRelease(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
