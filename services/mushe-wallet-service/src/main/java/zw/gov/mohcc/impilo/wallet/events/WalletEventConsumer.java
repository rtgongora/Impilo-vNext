package zw.gov.mohcc.impilo.wallet.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.card.CardHealthDataService;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.MerchantAccountEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.MerchantAccountRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka event consumer for the Mushe Wallet service.
 *
 * <p>Handles two categories of events:</p>
 * <ol>
 *   <li><strong>Payment/settlement events</strong> from MUSheX — credits merchant wallets
 *       when payments are completed or settlement batches are released.</li>
 *   <li><strong>Clinical encounter events</strong> from PCT — triggers health data updates
 *       on smart cards when clinical encounters are completed.</li>
 * </ol>
 */
@Component
public class WalletEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletEventConsumer.class);

    private final WalletRepository walletRepository;
    private final MerchantAccountRepository merchantRepository;
    private final CardRepository cardRepository;
    private final CardHealthDataService cardHealthDataService;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    public WalletEventConsumer(WalletRepository walletRepository,
                                MerchantAccountRepository merchantRepository,
                                CardRepository cardRepository,
                                CardHealthDataService cardHealthDataService,
                                WalletService walletService,
                                ObjectMapper objectMapper) {
        this.walletRepository = walletRepository;
        this.merchantRepository = merchantRepository;
        this.cardRepository = cardRepository;
        this.cardHealthDataService = cardHealthDataService;
        this.walletService = walletService;
        this.objectMapper = objectMapper;
    }

    // ── Payment Status Changed ──────────────────────────────────────────

    /**
     * Handle payment status changes from MUSheX.
     * When a payment is completed (status=PAID), credit the merchant wallet.
     */
    @KafkaListener(
            topics = {"mushex.payment.status.changed"},
            groupId = "mushe-wallet-service"
    )
    @Transactional
    public void onPaymentStatusChanged(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String status = node.path("status").asText();

            if (!"PAID".equals(status)) {
                log.debug("Ignoring payment event with status={}", status);
                return;
            }

            String merchantRef = node.path("merchantRef").asText("");
            String tenantIdStr = node.path("tenantId").asText("");
            BigDecimal amount = node.has("amount")
                    ? new BigDecimal(node.path("amount").asText("0"))
                    : BigDecimal.ZERO;
            String paymentIntentId = node.path("paymentIntentId").asText("");
            String description = node.path("description").asText("MUSheX payment");

            if (merchantRef.isBlank() || amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Payment event missing merchantRef or invalid amount: merchantRef={} amount={}",
                        merchantRef, amount);
                return;
            }

            // Find the merchant's wallet
            UUID tenantId = tenantIdStr.isBlank() ? null : UUID.fromString(tenantIdStr);
            Optional<MerchantAccountEntity> merchantOpt = tenantId != null
                    ? merchantRepository.findByTenantIdAndProviderNumber(tenantId, merchantRef)
                    : Optional.empty();

            if (merchantOpt.isEmpty()) {
                log.warn("Merchant not found for providerNumber={} tenantId={}", merchantRef, tenantId);
                return;
            }

            MerchantAccountEntity merchant = merchantOpt.get();
            creditWallet(merchant.getWalletId(), amount, "PAYMENT",
                    paymentIntentId, description);

            log.info("Credited merchant wallet walletId={} amount={} paymentIntentId={}",
                    merchant.getWalletId(), amount, paymentIntentId);

        } catch (Exception e) {
            log.error("Failed to process payment status event: {}", e.getMessage(), e);
        }
    }

    // ── Settlement Batch Released ────────────────────────────────────────

    /**
     * Handle settlement batch releases from MUSheX.
     * Credits provider/merchant wallets for their payout amounts.
     */
    @KafkaListener(
            topics = {"mushex.settlement.batch.released"},
            groupId = "mushe-wallet-service"
    )
    @Transactional
    public void onSettlementBatchReleased(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String batchId = node.path("batchId").asText("");

            JsonNode payouts = node.path("payouts");
            if (!payouts.isArray()) {
                log.warn("Settlement batch missing payouts array: batchId={}", batchId);
                return;
            }

            int credited = 0;
            for (JsonNode payout : payouts) {
                String merchantRef = payout.path("merchantRef").asText("");
                String tenantIdStr = payout.path("tenantId").asText("");
                BigDecimal amount = payout.has("amount")
                        ? new BigDecimal(payout.path("amount").asText("0"))
                        : BigDecimal.ZERO;

                if (merchantRef.isBlank() || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                UUID tenantId = tenantIdStr.isBlank() ? null : UUID.fromString(tenantIdStr);
                Optional<MerchantAccountEntity> merchantOpt = tenantId != null
                        ? merchantRepository.findByTenantIdAndProviderNumber(tenantId, merchantRef)
                        : Optional.empty();

                if (merchantOpt.isEmpty()) {
                    log.warn("Settlement payout — merchant not found: providerNumber={}", merchantRef);
                    continue;
                }

                MerchantAccountEntity merchant = merchantOpt.get();
                creditWallet(merchant.getWalletId(), amount, "SETTLEMENT",
                        batchId, "Settlement batch " + batchId);
                credited++;
            }

            log.info("Processed settlement batch batchId={} credited={} wallets", batchId, credited);

        } catch (Exception e) {
            log.error("Failed to process settlement batch event: {}", e.getMessage(), e);
        }
    }

    // ── Clinical Encounter Events (Card Health Data) ────────────────────

    /**
     * Handle clinical encounter completion events from PCT.
     * When an encounter is completed, queue a health data update for the patient's
     * card (if they have one linked via their wallet).
     */
    @KafkaListener(
            topics = {"impilo.pct.encounter", "pct.encounter"},
            groupId = "mushe-wallet-card"
    )
    @Transactional
    public void onEncounterCompleted(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            // Check the encounter status/event type
            String eventType = node.path("event_type").asText(node.path("eventType").asText(""));
            String status = node.path("status").asText(
                    node.path("payload").path("status").asText(""));

            // Only process completed encounters
            if (!isEncounterCompleted(eventType, status)) {
                return;
            }

            // Extract patient CPID
            String patientCpid = node.path("patientCpid").asText(
                    node.path("payload").path("patientCpid").asText(""));
            String tenantIdStr = node.path("tenant_id").asText(
                    node.path("tenantId").asText(""));

            if (patientCpid.isBlank()) {
                log.debug("Encounter event missing patientCpid, skipping card update");
                return;
            }

            UUID tenantId = tenantIdStr.isBlank() ? null : UUID.fromString(tenantIdStr);

            // Look up wallet by patient CPID
            Optional<WalletEntity> walletOpt = walletRepository
                    .findByOwnerRefAndOwnerType(patientCpid, "INDIVIDUAL");

            if (walletOpt.isEmpty()) {
                log.debug("No wallet found for patient CPID={}, skipping card update", patientCpid);
                return;
            }

            WalletEntity wallet = walletOpt.get();

            // Find active cards for this wallet
            List<CardEntity> cards = cardRepository.findByWalletIdAndStatus(wallet.getWalletId(), "ACTIVE");
            if (cards.isEmpty()) {
                log.debug("No active cards for walletId={}, skipping health data update",
                        wallet.getWalletId());
                return;
            }

            // Queue CRITICAL_SUMMARY update for each active card
            String encryptionKeyRef = "tshepo-keys:" + wallet.getTenantId() + ":patient:" + patientCpid;
            for (CardEntity card : cards) {
                // Fail-closed consent gate: only cache a clinical summary on cards whose holder has
                // opted the card into its PHR-carry function. No opt-in → no health data on the card.
                if (!card.isPhrEnabled()) {
                    log.debug("Card {} has not opted into PHR-carry; skipping health data update",
                            card.getCardId());
                    continue;
                }
                cardHealthDataService.updateCriticalSummary(
                        card.getCardId(),
                        patientCpid,
                        // Explicit PENDING_SYNC — the real critical summary is fetched from BUTANO
                        // at card-sync time. NOT "{}", which a reader could mistake for "no critical
                        // health data" (no allergies/conditions) on a health card.
                        "{\"status\":\"PENDING_SYNC\"}",
                        encryptionKeyRef,
                        tenantId != null ? tenantId : wallet.getTenantId()
                );

                log.info("Queued CRITICAL_SUMMARY update for cardId={} patientCpid={}",
                        card.getCardId(), patientCpid);
            }

        } catch (Exception e) {
            log.error("Failed to process encounter event: {}", e.getMessage(), e);
        }
    }

    // ── VITO Identity Events (Auto-Create Wallet) ────────────────────────

    /**
     * Handle VITO identity events.
     * When a patient registers (CLIENT_CREATED), auto-create an INDIVIDUAL wallet
     * so every registered patient has a wallet ready for use.
     */
    @KafkaListener(
            topics = {"vito.identity", "impilo.vito.identity"},
            groupId = "mushe-wallet-vito"
    )
    @Transactional
    public void onVitoIdentityEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            // Defensive: a mis-configured producer (JsonSerializer over a pre-serialized
            // JSON string) puts a quoted string literal on the wire — unwrap it so the
            // auto-create-wallet event isn't silently dropped as a blank type.
            if (node != null && node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }

            String eventType = node.path("event_type").asText(
                    node.path("eventType").asText(""));

            if (!"CLIENT_CREATED".equalsIgnoreCase(eventType)) {
                log.debug("Ignoring VITO identity event with type={}", eventType);
                return;
            }

            // Extract patient CPID — the unique person identifier
            String cpid = node.path("cpid").asText(
                    node.path("payload").path("cpid").asText(""));
            String tenantIdStr = node.path("tenant_id").asText(
                    node.path("tenantId").asText(""));
            String displayName = node.path("display_name").asText(
                    node.path("displayName").asText(
                            node.path("payload").path("display_name").asText("")));

            if (cpid.isBlank()) {
                log.warn("VITO CLIENT_CREATED event missing cpid, cannot auto-create wallet");
                return;
            }

            if (tenantIdStr.isBlank()) {
                log.warn("VITO CLIENT_CREATED event missing tenantId for cpid={}", cpid);
                return;
            }

            UUID tenantId = UUID.fromString(tenantIdStr);

            // Use WalletService.createWallet which is idempotent (returns existing if present)
            WalletEntity wallet = walletService.createWallet(
                    tenantId,
                    "INDIVIDUAL",
                    cpid,
                    displayName.isBlank() ? "Patient " + cpid : displayName,
                    null); // default currency

            log.info("Auto-created wallet for new patient: cpid={} walletId={} tenantId={}",
                    cpid, wallet.getWalletId(), tenantId);

        } catch (Exception e) {
            log.error("Failed to process VITO identity event: {}", e.getMessage(), e);
        }
    }

    // ── Internal Helpers ────────────────────────────────────────────────

    /**
     * Credit a wallet balance.
     */
    private void creditWallet(UUID walletId, BigDecimal amount, String txnType,
                               String reference, String description) {
        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));

        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
        walletRepository.save(wallet);

        // Note: TransactionEntity creation is handled by the wallet core service.
        // This consumer updates the balance directly for event-driven credits.
    }

    /**
     * Determine if an encounter event indicates completion.
     */
    private boolean isEncounterCompleted(String eventType, String status) {
        if ("COMPLETED".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) {
            return true;
        }
        if (eventType != null) {
            String lower = eventType.toLowerCase();
            return lower.contains("completed") || lower.contains("finished")
                    || lower.contains("discharged");
        }
        return false;
    }
}
