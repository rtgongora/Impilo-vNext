package zw.gov.mohcc.impilo.wallet.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.TransactionRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Core wallet operations: create wallets, credit, debit, transfer, balance inquiry.
 *
 * All mutations are transactional and write to the event outbox for reliable
 * Kafka publishing via the outbox pattern.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public WalletService(WalletRepository walletRepository,
                         TransactionRepository transactionRepository,
                         EventOutboxRepository eventOutboxRepository,
                         ObjectMapper objectMapper) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new wallet for the given owner. If a wallet already exists for the
     * same tenant/ownerType/ownerRef combination, returns the existing wallet.
     */
    @Transactional
    public WalletEntity createWallet(UUID tenantId,
                                     String ownerType,
                                     String ownerRef,
                                     String displayName,
                                     String currency) {
        Optional<WalletEntity> existing = walletRepository.findByTenantIdAndOwnerTypeAndOwnerRef(
                tenantId, ownerType, ownerRef);
        if (existing.isPresent()) {
            log.info("Wallet already exists for tenant={} owner={}:{}, returning existing walletId={}",
                    tenantId, ownerType, ownerRef, existing.get().getWalletId());
            return existing.get();
        }

        WalletEntity wallet = new WalletEntity();
        wallet.setTenantId(tenantId);
        wallet.setOwnerType(ownerType);
        wallet.setOwnerRef(ownerRef);
        wallet.setDisplayName(displayName);
        wallet.setCurrency(currency != null ? currency : "ZWL");

        wallet = walletRepository.save(wallet);

        log.info("Created wallet: walletId={} owner={}:{} currency={}",
                wallet.getWalletId(), ownerType, ownerRef, wallet.getCurrency());

        publishOutboxEvent("WALLET", wallet.getWalletId().toString(),
                "wallet.created", tenantId, wallet.getOwnerRef(), ownerType,
                null, buildWalletPayload(wallet));

        return wallet;
    }

    /**
     * Retrieves a wallet by walletId within a tenant.
     */
    @Transactional(readOnly = true)
    public WalletEntity getWallet(UUID tenantId, UUID walletId) {
        return walletRepository.findByWalletId(walletId)
                .filter(w -> w.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NoSuchElementException(
                        "Wallet not found: " + walletId));
    }

    /**
     * Retrieves a wallet by owner type and reference within a tenant.
     */
    @Transactional(readOnly = true)
    public WalletEntity getWalletByOwner(UUID tenantId, String ownerType, String ownerRef) {
        return walletRepository.findByTenantIdAndOwnerTypeAndOwnerRef(tenantId, ownerType, ownerRef)
                .orElseThrow(() -> new NoSuchElementException(
                        "Wallet not found for owner: " + ownerType + ":" + ownerRef));
    }

    /**
     * Credits a wallet (increases balance). The wallet must be ACTIVE.
     *
     * @return the created transaction record
     */
    @Transactional
    public TransactionEntity credit(UUID walletId,
                                    BigDecimal amount,
                                    String txnType,
                                    String channel,
                                    String reference,
                                    String description,
                                    String counterpartyRef,
                                    String counterpartyName,
                                    UUID correlationId,
                                    String idempotencyKey) {
        // Idempotency check
        if (idempotencyKey != null) {
            Optional<TransactionEntity> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotency hit for credit: key={}", idempotencyKey);
                return existing.get();
            }
        }

        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));
        requireActiveWallet(wallet);

        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
        walletRepository.save(wallet);

        TransactionEntity txn = new TransactionEntity();
        txn.setTenantId(wallet.getTenantId());
        txn.setWalletId(walletId);
        txn.setTxnType(txnType);
        txn.setDirection("CREDIT");
        txn.setAmount(amount);
        txn.setFee(BigDecimal.ZERO);
        txn.setNetAmount(amount);
        txn.setCurrency(wallet.getCurrency());
        txn.setBalanceAfter(wallet.getBalance());
        txn.setCounterpartyRef(counterpartyRef);
        txn.setCounterpartyName(counterpartyName);
        txn.setReference(reference);
        txn.setDescription(description);
        txn.setChannel(channel);
        txn.setStatus("COMPLETED");
        txn.setIdempotencyKey(idempotencyKey);
        txn.setCorrelationId(correlationId);
        txn = transactionRepository.save(txn);

        log.info("Credited wallet={} amount={} txnId={} ref={}",
                walletId, amount, txn.getTxnId(), reference);

        publishOutboxEvent("TRANSACTION", txn.getTxnId().toString(),
                "wallet.credited", wallet.getTenantId(), wallet.getOwnerRef(), wallet.getOwnerType(),
                correlationId, buildTransactionPayload(txn, wallet));

        return txn;
    }

    /**
     * Debits a wallet (decreases balance). The wallet must be ACTIVE and have
     * sufficient available balance.
     *
     * @return the created transaction record
     */
    @Transactional
    public TransactionEntity debit(UUID walletId,
                                   BigDecimal amount,
                                   String txnType,
                                   String channel,
                                   String reference,
                                   String description,
                                   String counterpartyRef,
                                   String counterpartyName,
                                   UUID correlationId,
                                   String idempotencyKey) {
        // Idempotency check
        if (idempotencyKey != null) {
            Optional<TransactionEntity> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotency hit for debit: key={}", idempotencyKey);
                return existing.get();
            }
        }

        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));
        requireActiveWallet(wallet);
        requireSufficientBalance(wallet, amount);
        requireWithinDailyLimit(wallet, amount);
        requireWithinMonthlyLimit(wallet, amount);

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        walletRepository.save(wallet);

        TransactionEntity txn = new TransactionEntity();
        txn.setTenantId(wallet.getTenantId());
        txn.setWalletId(walletId);
        txn.setTxnType(txnType);
        txn.setDirection("DEBIT");
        txn.setAmount(amount);
        txn.setFee(BigDecimal.ZERO);
        txn.setNetAmount(amount);
        txn.setCurrency(wallet.getCurrency());
        txn.setBalanceAfter(wallet.getBalance());
        txn.setCounterpartyRef(counterpartyRef);
        txn.setCounterpartyName(counterpartyName);
        txn.setReference(reference);
        txn.setDescription(description);
        txn.setChannel(channel);
        txn.setStatus("COMPLETED");
        txn.setIdempotencyKey(idempotencyKey);
        txn.setCorrelationId(correlationId);
        txn = transactionRepository.save(txn);

        log.info("Debited wallet={} amount={} txnId={} ref={}",
                walletId, amount, txn.getTxnId(), reference);

        publishOutboxEvent("TRANSACTION", txn.getTxnId().toString(),
                "wallet.debited", wallet.getTenantId(), wallet.getOwnerRef(), wallet.getOwnerType(),
                correlationId, buildTransactionPayload(txn, wallet));

        return txn;
    }

    /**
     * Transfers funds from one wallet to another. Creates a debit on the source
     * wallet and a credit on the destination wallet within a single transaction.
     *
     * @return the debit transaction on the source wallet
     */
    @Transactional
    public TransactionEntity transfer(UUID fromWalletId,
                                      UUID toWalletId,
                                      BigDecimal amount,
                                      String reference,
                                      String description,
                                      String channel,
                                      UUID correlationId,
                                      String idempotencyKey) {
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        String debitKey = idempotencyKey != null ? idempotencyKey + ":DEBIT" : null;
        String creditKey = idempotencyKey != null ? idempotencyKey + ":CREDIT" : null;

        WalletEntity toWallet = walletRepository.findByWalletId(toWalletId)
                .orElseThrow(() -> new NoSuchElementException("Destination wallet not found: " + toWalletId));

        TransactionEntity debitTxn = debit(fromWalletId, amount, "TRANSFER_OUT", channel, reference,
                description, toWallet.getOwnerRef(), toWallet.getDisplayName(),
                correlationId, debitKey);

        credit(toWalletId, amount, "TRANSFER_IN", channel, reference,
                description,
                debitTxn.getCounterpartyRef() != null ? debitTxn.getCounterpartyRef() : fromWalletId.toString(),
                null, correlationId, creditKey);

        log.info("Transfer completed: from={} to={} amount={} ref={}",
                fromWalletId, toWalletId, amount, reference);

        return debitTxn;
    }

    /**
     * Returns the current balance of a wallet.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID walletId) {
        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));
        return wallet.getAvailableBalance();
    }

    /**
     * Returns a paginated list of transactions for a wallet.
     */
    @Transactional(readOnly = true)
    public Page<TransactionEntity> getTransactions(UUID walletId, Pageable pageable) {
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
    }

    // ── Guard methods ──────────────────────────────────────────────────────

    private void requireActiveWallet(WalletEntity wallet) {
        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalStateException("Wallet is not active: status=" + wallet.getStatus()
                    + " walletId=" + wallet.getWalletId());
        }
    }

    private void requireSufficientBalance(WalletEntity wallet, BigDecimal amount) {
        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient available balance: available="
                    + wallet.getAvailableBalance() + " requested=" + amount
                    + " walletId=" + wallet.getWalletId());
        }
    }

    private void requireWithinDailyLimit(WalletEntity wallet, BigDecimal amount) {
        if (wallet.getDailyLimit() == null) {
            return; // No daily limit configured
        }
        BigDecimal dailyTotal = getDailyTotal(wallet.getWalletId(), LocalDate.now());
        if (dailyTotal.add(amount).compareTo(wallet.getDailyLimit()) > 0) {
            throw new IllegalStateException("Daily transaction limit exceeded: dailyTotal="
                    + dailyTotal + " requested=" + amount + " dailyLimit=" + wallet.getDailyLimit()
                    + " walletId=" + wallet.getWalletId());
        }
    }

    private void requireWithinMonthlyLimit(WalletEntity wallet, BigDecimal amount) {
        if (wallet.getMonthlyLimit() == null) {
            return; // No monthly limit configured
        }
        LocalDate now = LocalDate.now();
        BigDecimal monthlyTotal = getMonthlyTotal(wallet.getWalletId(), now.getYear(), now.getMonthValue());
        if (monthlyTotal.add(amount).compareTo(wallet.getMonthlyLimit()) > 0) {
            throw new IllegalStateException("Monthly transaction limit exceeded: monthlyTotal="
                    + monthlyTotal + " requested=" + amount + " monthlyLimit=" + wallet.getMonthlyLimit()
                    + " walletId=" + wallet.getWalletId());
        }
    }

    /**
     * Returns the sum of all debit transaction amounts for the given wallet on a specific date.
     */
    BigDecimal getDailyTotal(UUID walletId, LocalDate date) {
        return transactionRepository.sumDebitsByDate(walletId, date);
    }

    /**
     * Returns the sum of all debit transaction amounts for the given wallet in a specific month.
     */
    BigDecimal getMonthlyTotal(UUID walletId, int year, int month) {
        return transactionRepository.sumDebitsByMonth(walletId, year, month);
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
                    + ":" + (correlationId != null ? correlationId : UUID.randomUUID()));
            event.setOccurredAt(OffsetDateTime.now());
            eventOutboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: aggregateType={} eventType={}", aggregateType, eventType, e);
        }
    }

    private String buildWalletPayload(WalletEntity wallet) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("walletId", wallet.getWalletId().toString());
        payload.put("tenantId", wallet.getTenantId().toString());
        payload.put("ownerType", wallet.getOwnerType());
        payload.put("ownerRef", wallet.getOwnerRef());
        payload.put("currency", wallet.getCurrency());
        payload.put("status", wallet.getStatus());
        return toJson(payload);
    }

    private String buildTransactionPayload(TransactionEntity txn, WalletEntity wallet) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("txnId", txn.getTxnId().toString());
        payload.put("walletId", txn.getWalletId().toString());
        payload.put("txnType", txn.getTxnType());
        payload.put("direction", txn.getDirection());
        payload.put("amount", txn.getAmount().toPlainString());
        payload.put("fee", txn.getFee() != null ? txn.getFee().toPlainString() : "0");
        payload.put("netAmount", txn.getNetAmount().toPlainString());
        payload.put("currency", txn.getCurrency());
        payload.put("balanceAfter", txn.getBalanceAfter().toPlainString());
        payload.put("counterpartyRef", txn.getCounterpartyRef());
        payload.put("counterpartyName", txn.getCounterpartyName());
        payload.put("reference", txn.getReference());
        payload.put("channel", txn.getChannel());
        payload.put("status", txn.getStatus());
        // MUSHEX ledger reconciliation fields — allows MUSHeX event consumer to
        // pick up wallet transactions and record them in the double-entry ledger
        payload.put("mushex_ledger_sync", true);
        payload.put("wallet_owner_ref", wallet.getOwnerRef());
        payload.put("wallet_owner_type", wallet.getOwnerType());
        return toJson(payload);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to serialize payload to JSON", e);
            return "{}";
        }
    }
}
