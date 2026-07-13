package zw.gov.mohcc.impilo.mushex.service.disbursement;

import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutItemEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;

import java.util.List;
import java.util.UUID;

/**
 * A payout disbursement rail — the leg that actually moves settlement money to
 * payees. Mirrors the {@code PaymentRailAdapter} neutrality model on the
 * outbound side: everything upstream (settlement computation, payout batches,
 * release authorization, ledger) is rail-agnostic; a rail is one implementation
 * of this interface registered by {@link AdapterType}.
 *
 * <p>Live today: {@link WalletDisbursementRail} (internal Mushe-wallet credits).
 * External rails (ZIPIT B2C bulk bank payouts, EcoCash B2C) drop in behind the
 * same seam once provider agreements/credentials exist — a batch whose rail is
 * not registered stays PENDING (fail-closed), never fake-completed.</p>
 */
public interface DisbursementRail {

    AdapterType railType();

    /**
     * Disburse the batch's items. Implementations must be idempotent per item
     * (a replayed disbursement must never pay a payee twice) and must never
     * throw for per-item failures — failures come back in the outcome.
     */
    RailOutcome disburse(UUID tenantId, String batchId, List<PayoutItemEntity> items);

    /** Per-item disbursement result. */
    record ItemOutcome(String itemId, boolean credited, String reason) {}

    /** Whole-batch outcome; {@code complete()} when every item credited. */
    record RailOutcome(List<ItemOutcome> items) {
        public boolean complete() {
            return items.stream().allMatch(ItemOutcome::credited);
        }

        public long creditedCount() {
            return items.stream().filter(ItemOutcome::credited).count();
        }
    }
}
