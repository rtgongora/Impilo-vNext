package zw.gov.mohcc.impilo.mushex.service.disbursement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutItemEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.integration.MusheWalletAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tier-1 payout rail: credits payee merchant wallets in mushe-wallet-service —
 * real internal money movement, no external dependency. Vendors receive
 * spendable wallet balance the moment a settlement's payouts are released.
 *
 * <p>Idempotency: each item's wallet credit carries a deterministic
 * reference/idempotency seed ({@code payout:<batchId>:<itemId>} via the
 * adapter), so a replayed release can never credit a payee twice.</p>
 */
@Component
public class WalletDisbursementRail implements DisbursementRail {

    private static final Logger log = LoggerFactory.getLogger(WalletDisbursementRail.class);

    private final MusheWalletAdapter musheWalletAdapter;

    public WalletDisbursementRail(MusheWalletAdapter musheWalletAdapter) {
        this.musheWalletAdapter = musheWalletAdapter;
    }

    @Override
    public AdapterType railType() {
        return AdapterType.WALLET;
    }

    @Override
    public RailOutcome disburse(UUID tenantId, String batchId, List<PayoutItemEntity> items) {
        List<Map<String, Object>> payoutItems = new ArrayList<>(items.size());
        for (PayoutItemEntity item : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("merchantProviderNumber", item.getPayeeRef());
            m.put("amount", item.getAmount());
            m.put("reference", "payout:" + batchId + ":" + item.getId());
            payoutItems.add(m);
        }

        MusheWalletAdapter.DisbursementResult result =
                musheWalletAdapter.disburseToBatch(tenantId, payoutItems);

        // Map adapter failures back to items by the deterministic reference.
        Map<String, String> failureByReference = new LinkedHashMap<>();
        for (MusheWalletAdapter.FailedDisbursement f : result.failures()) {
            failureByReference.put(f.reference(), f.reason());
        }

        List<ItemOutcome> outcomes = new ArrayList<>(items.size());
        for (PayoutItemEntity item : items) {
            String ref = "payout:" + batchId + ":" + item.getId();
            String failure = failureByReference.get(ref);
            outcomes.add(new ItemOutcome(item.getId(), failure == null, failure));
        }
        log.info("Wallet disbursement for batch {}: {}/{} credited",
                batchId, result.credited(), items.size());
        return new RailOutcome(outcomes);
    }
}
