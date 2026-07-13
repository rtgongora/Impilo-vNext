package zw.gov.mohcc.impilo.vito.core.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.WalletJournalEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.WalletJournalRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Health Payments Wallet — Double-Entry Ledger.
 *
 * <p><b>Deprecated — money is mushe-wallet-service's SoR.</b> This is a parallel money ledger that
 * duplicates {@code mushe-wallet-service}, which already owns the wallet money
 * ({@code /internal/v1/wallets}: credit/debit/transfer/balance/transactions) AND the SMART Card
 * subsystem ({@code /internal/v1/cards}: create/activate/block/replace/verify-pin). The correct split
 * is: <b>VITO owns the card as an identity credential</b> (Health ID binding, key/DID, revocation);
 * <b>mushe-wallet-service owns the card's stored value and all money movement</b>. New code must use
 * mushe-wallet-service; this ledger is retained only until {@code RecoveryService} secure-handover is
 * repointed to mushe's card-replace/wallet-transfer. See {@code docs/registry/system-of-record-map.md}.
 *
 * <p>Every monetary movement creates exactly TWO journal entries (DEBIT source + CREDIT destination);
 * idempotent, pessimistic-locked, auditable, offline-capable.
 */
@Deprecated
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletJournalRepository journalRepository;
    private final EventOutboxRepository outboxRepository;

    public WalletService(WalletRepository walletRepository,
                         WalletJournalRepository journalRepository,
                         EventOutboxRepository outboxRepository) {
        this.walletRepository = walletRepository;
        this.journalRepository = journalRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Create a wallet for a client (one per client per currency).
     */
    @Transactional
    public WalletEntity createWallet(UUID tenantId, UUID healthId, Long cardId, String currency) {
        walletRepository.findByTenantIdAndHealthIdAndCurrency(tenantId, healthId, currency)
                .ifPresent(w -> { throw new IllegalStateException("Wallet already exists"); });

        WalletEntity wallet = new WalletEntity();
        wallet.setTenantId(tenantId);
        wallet.setHealthId(healthId);
        wallet.setCardId(cardId);
        wallet.setCurrency(currency);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setStatus("ACTIVE");

        return walletRepository.save(wallet);
    }

    /**
     * Top up a wallet (credit from external source).
     * Creates a single CREDIT journal entry with TOPUP counterparty.
     */
    @Transactional
    public WalletJournalEntity topUp(UUID tenantId, UUID healthId, String currency,
                                      BigDecimal amount, String transactionRef, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }

        WalletEntity wallet = walletRepository
                .findWithLockByTenantIdAndHealthIdAndCurrency(tenantId, healthId, currency)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        WalletJournalEntity entry = createJournalEntry(
                tenantId, transactionRef, wallet.getId(), "CREDIT",
                amount, newBalance, "TOPUP", null, description);

        publishEvent("WALLET", wallet.getId().toString(), "WALLET_TOPPED_UP",
                String.format("{\"walletId\":%d,\"amount\":\"%s\",\"newBalance\":\"%s\"}",
                        wallet.getId(), amount, newBalance));

        return entry;
    }

    /**
     * Pay from wallet (debit for health service).
     * Creates a DEBIT journal entry.
     */
    @Transactional
    public WalletJournalEntity pay(UUID tenantId, UUID healthId, String currency,
                                    BigDecimal amount, String transactionRef,
                                    String counterpartyType, String counterpartyRef,
                                    String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        WalletEntity wallet = walletRepository
                .findWithLockByTenantIdAndHealthIdAndCurrency(tenantId, healthId, currency)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        WalletJournalEntity entry = createJournalEntry(
                tenantId, transactionRef, wallet.getId(), "DEBIT",
                amount, newBalance, counterpartyType, counterpartyRef, description);

        publishEvent("WALLET", wallet.getId().toString(), "WALLET_PAYMENT",
                String.format("{\"walletId\":%d,\"amount\":\"%s\",\"newBalance\":\"%s\",\"counterparty\":\"%s\"}",
                        wallet.getId(), amount, newBalance, counterpartyRef));

        return entry;
    }

    /**
     * Record an offline transaction (signed with card's private key).
     * Marked as unreconciled until the card syncs.
     */
    @Transactional
    public WalletJournalEntity recordOfflineTransaction(UUID tenantId, UUID healthId, String currency,
                                                         BigDecimal amount, String transactionRef,
                                                         String counterpartyType, String counterpartyRef,
                                                         String jwsSignature, OffsetDateTime offlineTimestamp) {
        WalletEntity wallet = walletRepository
                .findWithLockByTenantIdAndHealthIdAndCurrency(tenantId, healthId, currency)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        WalletJournalEntity entry = createJournalEntry(
                tenantId, transactionRef, wallet.getId(), "DEBIT",
                amount, newBalance, counterpartyType, counterpartyRef,
                "Offline transaction");
        entry.setOfflineSignature(jwsSignature);
        entry.setOfflineTimestamp(offlineTimestamp);
        entry.setReconciled(false);

        return journalRepository.save(entry);
    }

    /**
     * Transfer wallet balance (used during Secure Handover).
     */
    @Transactional
    public void transferBalance(UUID tenantId, UUID fromHealthId, UUID toHealthId,
                                 String currency, String transactionRef) {
        WalletEntity source = walletRepository
                .findWithLockByTenantIdAndHealthIdAndCurrency(tenantId, fromHealthId, currency)
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        WalletEntity dest = walletRepository
                .findWithLockByTenantIdAndHealthIdAndCurrency(tenantId, toHealthId, currency)
                .orElseThrow(() -> new IllegalArgumentException("Destination wallet not found"));

        BigDecimal amount = source.getBalance();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        // Debit source
        source.setBalance(BigDecimal.ZERO);
        walletRepository.save(source);
        createJournalEntry(tenantId, transactionRef, source.getId(), "DEBIT",
                amount, BigDecimal.ZERO, "SYSTEM", "TRANSFER_OUT", "Secure handover transfer");

        // Credit destination
        BigDecimal newDestBalance = dest.getBalance().add(amount);
        dest.setBalance(newDestBalance);
        walletRepository.save(dest);
        createJournalEntry(tenantId, transactionRef, dest.getId(), "CREDIT",
                amount, newDestBalance, "SYSTEM", "TRANSFER_IN", "Secure handover transfer");
    }

    @Transactional(readOnly = true)
    public Optional<WalletEntity> getWallet(UUID tenantId, UUID healthId) {
        return walletRepository.findByTenantIdAndHealthId(tenantId, healthId);
    }

    @Transactional(readOnly = true)
    public Page<WalletJournalEntity> getJournal(Long walletId, Pageable pageable) {
        return journalRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
    }

    // --- Helpers ---

    private WalletJournalEntity createJournalEntry(UUID tenantId, String transactionRef,
                                                    Long walletId, String entryType,
                                                    BigDecimal amount, BigDecimal runningBalance,
                                                    String counterpartyType, String counterpartyRef,
                                                    String description) {
        WalletJournalEntity entry = new WalletJournalEntity();
        entry.setTenantId(tenantId);
        entry.setTransactionRef(transactionRef);
        entry.setWalletId(walletId);
        entry.setEntryType(entryType);
        entry.setAmount(amount);
        entry.setRunningBalance(runningBalance);
        entry.setCounterpartyType(counterpartyType);
        entry.setCounterpartyRef(counterpartyRef);
        entry.setDescription(description);
        return journalRepository.save(entry);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
