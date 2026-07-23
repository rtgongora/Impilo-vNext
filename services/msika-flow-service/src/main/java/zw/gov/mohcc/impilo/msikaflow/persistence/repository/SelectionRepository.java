package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SelectionEntity;

import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SelectionRepository extends JpaRepository<SelectionEntity, String> {

    List<SelectionEntity> findByRequestId(String requestId);

    Optional<SelectionEntity> findFirstByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    /** OF-B10 — payment-callback resume lookup (intent → held selection). */
    Optional<SelectionEntity> findFirstByPaymentIntentId(String paymentIntentId);

    /** OF-B10 — AWAITING_PAYMENT timeout sweep input. */
    List<SelectionEntity> findByStatus(SelectionStatus status);

    /** OF-B17 — dispatch retry-sweep input (committed selections whose dispatch failed). */
    List<SelectionEntity> findByStatusAndDispatchStatus(SelectionStatus status, String dispatchStatus);

    /** OF-B17 — Nhume delivery id → selection (write-back cross-check). */
    Optional<SelectionEntity> findFirstByDispatchRef(String dispatchRef);

    /** OF-B14 — all members of one split combination (§8.6.4). */
    List<SelectionEntity> findBySplitGroupId(String splitGroupId);

    /** OF-B29 — anomaly sweep input: commitments older than the monitor cutoff. */
    List<SelectionEntity> findByStatusAndCommittedAtBefore(SelectionStatus status, java.time.OffsetDateTime cutoff);

    /** OF-B29 — fairness-window input: commitments inside the observation window. */
    List<SelectionEntity> findByStatusAndCommittedAtAfter(SelectionStatus status, java.time.OffsetDateTime cutoff);
}
