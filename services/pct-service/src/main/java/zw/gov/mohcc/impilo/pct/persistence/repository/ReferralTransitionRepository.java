package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralTransitionEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Append-only ledger of referral state transitions (TM-B1).
 */
@Repository
public interface ReferralTransitionRepository extends JpaRepository<ReferralTransitionEntity, Long> {

    List<ReferralTransitionEntity> findByReferralIdOrderByCreatedAtAsc(UUID referralId);

    /** Shadow→enforce flip criterion: count flagged violations in a window. */
    long countByAllowedFalseAndCreatedAtAfter(OffsetDateTime since);
}
