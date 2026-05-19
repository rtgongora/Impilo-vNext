package zw.gov.mohcc.impilo.wallet.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.HoldEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.HoldRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.TransactionRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Hold lifecycle: place, capture, release, and auto-expire holds.
 *
 * A hold reduces the available_balance of a wallet without reducing the ledger
 * balance. Capturing a hold converts it into a debit transaction. Releasing a
 * hold restores the available_balance.
 */
@Service
public class HoldService {

    private static final Logger log = LoggerFactory.getLogger(HoldService.class);

    private final HoldRepository holdRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public HoldService(HoldRepository holdRepository,
                       WalletRepository walletRepository,
                       TransactionRepository transactionRepository,
                       EventOutboxRepository eventOutboxRepository,
                       ObjectMapper objectMapper) {
        this.holdRepository = holdRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Places a hold on a wallet, reducing available_balance by the hold amount.
     * The wallet must be ACTIVE and have sufficient available balance.
     */
    @Transactional
    public HoldEntity placeHold(UUID walletId,
                                BigDecimal amount,
                                String reason,
                                String reference,
                                OffsetDateTime expiresAt) {
        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));

        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalStateException("Wallet is not active: status=" + wallet.getStatus());
        }

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient available balance for hold: available="
                    + wallet.getAvailableBalance() + " requested=" + amount);
        }

        // Deduct from available_balance, add to held_balance
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        wallet.setHeldBalance(wallet.getHeldBalance().add(amount));
        walletRepository.save(wallet);

        HoldEntity hold = new HoldEntity();
        hold.setTenantId(wallet.getTenantId());
        hold.setWalletId(walletId);
        hold.setAmount(amount);
        hold.setReason(reason);
        hold.setReference(reference);
        hold.setStatus("ACTIVE");
        hold.setExpiresAt(expiresAt);
        hold = holdRepository.save(hold);

        log.info("Placed hold: holdId={} walletId={} amount={} expiresAt={}",
                hold.getHoldId(), walletId, amount, expiresAt);

        publishOutboxEvent("HOLD", hold.getHoldId().toString(),
                "hold.placed", wallet.getTenantId(), wallet.getOwnerRef(), wallet.getOwnerType(),
                null, buildHoldPayload(hold));

        return hold;
    }

    /**
     * Captures a hold, converting it into a debit transaction on the wallet.
     * The hold must be in ACTIVE status.
     */
    @Transactional
    public HoldEntity captureHold(UUID holdId) {
        HoldEntity hold = holdRepository.findByHoldId(holdId)
                .orElseThrow(() -> new NoSuchElementException("Hold not found: " + holdId));

        if (!"ACTIVE".equals(hold.getStatus())) {
            throw new IllegalStateException("Hold is not active: status=" + hold.getStatus()
                    + " holdId=" + holdId);
        }

        UUID holdWalletId = hold.getWalletId();
        WalletEntity wallet = walletRepository.findByWalletId(holdWalletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + holdWalletId));

        // Move from held_balance to actual balance deduction
        wallet.setBalance(wallet.getBalance().subtract(hold.getAmount()));
        wallet.setHeldBalance(wallet.getHeldBalance().subtract(hold.getAmount()));
        walletRepository.save(wallet);

        // Mark hold as captured
        hold.setStatus("CAPTURED");
        hold.setCapturedAt(OffsetDateTime.now());
        hold = holdRepository.save(hold);

        // Create the debit transaction record for the capture
        TransactionEntity txn = new TransactionEntity();
        txn.setTenantId(wallet.getTenantId());
        txn.setWalletId(hold.getWalletId());
        txn.setTxnType("HOLD_CAPTURE");
        txn.setDirection("DEBIT");
        txn.setAmount(hold.getAmount());
        txn.setFee(BigDecimal.ZERO);
        txn.setNetAmount(hold.getAmount());
        txn.setCurrency(wallet.getCurrency());
        txn.setBalanceAfter(wallet.getBalance());
        txn.setReference(hold.getReference());
        txn.setDescription("Hold captured: " + hold.getReason());
        txn.setStatus("COMPLETED");
        txn.setIdempotencyKey("hold-capture:" + holdId);
        transactionRepository.save(txn);

        log.info("Captured hold: holdId={} walletId={} amount={}",
                holdId, hold.getWalletId(), hold.getAmount());

        publishOutboxEvent("HOLD", hold.getHoldId().toString(),
                "hold.captured", wallet.getTenantId(), wallet.getOwnerRef(), wallet.getOwnerType(),
                null, buildHoldPayload(hold));

        return hold;
    }

    /**
     * Releases a hold, restoring the held amount back to available_balance.
     * The hold must be in ACTIVE status.
     */
    @Transactional
    public HoldEntity releaseHold(UUID holdId) {
        HoldEntity hold = holdRepository.findByHoldId(holdId)
                .orElseThrow(() -> new NoSuchElementException("Hold not found: " + holdId));

        if (!"ACTIVE".equals(hold.getStatus())) {
            throw new IllegalStateException("Hold is not active: status=" + hold.getStatus()
                    + " holdId=" + holdId);
        }

        UUID holdWalletId = hold.getWalletId();
        WalletEntity wallet = walletRepository.findByWalletId(holdWalletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + holdWalletId));

        // Restore available_balance, reduce held_balance
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(hold.getAmount()));
        wallet.setHeldBalance(wallet.getHeldBalance().subtract(hold.getAmount()));
        walletRepository.save(wallet);

        hold.setStatus("RELEASED");
        hold.setReleasedAt(OffsetDateTime.now());
        hold = holdRepository.save(hold);

        log.info("Released hold: holdId={} walletId={} amount={}",
                holdId, hold.getWalletId(), hold.getAmount());

        publishOutboxEvent("HOLD", hold.getHoldId().toString(),
                "hold.released", wallet.getTenantId(), wallet.getOwnerRef(), wallet.getOwnerType(),
                null, buildHoldPayload(hold));

        return hold;
    }

    /**
     * Scheduled job that releases all expired holds. Runs every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${mushe.holds.expire-interval-ms:300000}")
    @Transactional
    public void expireHolds() {
        List<HoldEntity> expired = holdRepository.findByStatusAndExpiresAtBefore("ACTIVE", OffsetDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("Expiring {} holds", expired.size());

        for (HoldEntity hold : expired) {
            try {
                WalletEntity wallet = walletRepository.findByWalletId(hold.getWalletId()).orElse(null);
                if (wallet == null) {
                    log.warn("Wallet not found for expired hold: holdId={} walletId={}",
                            hold.getHoldId(), hold.getWalletId());
                    continue;
                }

                wallet.setAvailableBalance(wallet.getAvailableBalance().add(hold.getAmount()));
                wallet.setHeldBalance(wallet.getHeldBalance().subtract(hold.getAmount()));
                walletRepository.save(wallet);

                hold.setStatus("EXPIRED");
                hold.setReleasedAt(OffsetDateTime.now());
                holdRepository.save(hold);

                log.info("Expired hold: holdId={} walletId={} amount={}",
                        hold.getHoldId(), hold.getWalletId(), hold.getAmount());

                publishOutboxEvent("HOLD", hold.getHoldId().toString(),
                        "hold.expired", wallet.getTenantId(), wallet.getOwnerRef(), wallet.getOwnerType(),
                        null, buildHoldPayload(hold));
            } catch (Exception e) {
                log.error("Failed to expire hold: holdId={}", hold.getHoldId(), e);
            }
        }
    }

    // ── Outbox event publishing ────────────────────────────────────────────

    private void publishOutboxEvent(String aggregateType,
                                    String aggregateId,
                                    String eventType,
                                    UUID tenantId,
                                    String subjectId,
                                    String subjectType,
                                    UUID correlationId,
                                    String payloadJson) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setTenantId(tenantId);
            event.setSubjectId(subjectId);
            event.setSubjectType(subjectType);
            event.setCorrelationId(correlationId);
            event.setPayloadJson(payloadJson);
            event.setIdempotencyKey("mushe-wallet:" + aggregateType + ":" + aggregateId + ":" + eventType
                    + ":" + UUID.randomUUID());
            event.setOccurredAt(OffsetDateTime.now());
            eventOutboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: aggregateType={} eventType={}", aggregateType, eventType, e);
        }
    }

    private String buildHoldPayload(HoldEntity hold) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("holdId", hold.getHoldId().toString());
        payload.put("walletId", hold.getWalletId().toString());
        payload.put("amount", hold.getAmount().toPlainString());
        payload.put("reason", hold.getReason());
        payload.put("reference", hold.getReference());
        payload.put("status", hold.getStatus());
        payload.put("expiresAt", hold.getExpiresAt().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
