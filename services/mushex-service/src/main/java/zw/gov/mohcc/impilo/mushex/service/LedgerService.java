package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerAccountEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerEntryEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AccountType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.LedgerAccountRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.LedgerEntryRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Double-entry-lite ledger service.
 *
 * Maintains a set of predefined accounts per tenant and records journal entries
 * as balanced debit/credit pairs. Every financial event (payment, refund,
 * settlement, platform fee) is captured as an immutable ledger entry.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    /** Default account names that every tenant must have. */
    private static final Map<String, AccountType> DEFAULT_ACCOUNTS = new LinkedHashMap<>();

    static {
        DEFAULT_ACCOUNTS.put("PATIENT_CASH", AccountType.ASSET);
        DEFAULT_ACCOUNTS.put("FACILITY_REVENUE", AccountType.INCOME);
        DEFAULT_ACCOUNTS.put("PLATFORM_FEES", AccountType.INCOME);
        DEFAULT_ACCOUNTS.put("INSURER_RECEIVABLE", AccountType.ASSET);
        DEFAULT_ACCOUNTS.put("REFUNDS_PAYABLE", AccountType.LIABILITY);
        DEFAULT_ACCOUNTS.put("SETTLEMENT_PAYABLE", AccountType.LIABILITY);
    }

    private final LedgerAccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public LedgerService(LedgerAccountRepository accountRepository,
                         LedgerEntryRepository entryRepository,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ensure all default accounts exist for the given tenant.
     * Idempotent: skips accounts that already exist.
     */
    @Transactional
    public void ensureAccounts(UUID tenantId) {
        for (Map.Entry<String, AccountType> entry : DEFAULT_ACCOUNTS.entrySet()) {
            String name = entry.getKey();
            AccountType type = entry.getValue();

            if (accountRepository.findByTenantIdAndName(tenantId, name).isEmpty()) {
                LedgerAccountEntity account = new LedgerAccountEntity();
                account.setAccountId(UlidGenerator.generate());
                account.setTenantId(tenantId);
                account.setName(name);
                account.setAccountType(type);
                account.setCurrency("USD");
                accountRepository.save(account);
                log.info("Created ledger account: tenant={}, name={}, type={}", tenantId, name, type);
            }
        }
    }

    /**
     * Post a patient payment: Debit PATIENT_CASH, Credit FACILITY_REVENUE.
     */
    @Transactional
    public LedgerEntryEntity postPayment(UUID tenantId, String intentId, BigDecimal amount, String currency) {
        ensureAccounts(tenantId);

        LedgerAccountEntity debit = resolveAccount(tenantId, "PATIENT_CASH");
        LedgerAccountEntity credit = resolveAccount(tenantId, "FACILITY_REVENUE");

        LedgerEntryEntity entry = createEntry(
                tenantId, intentId, "PAYMENT", intentId,
                debit.getAccountId(), credit.getAccountId(),
                amount, currency,
                "Patient payment received for intent " + intentId
        );

        log.info("Posted payment ledger entry: tenant={}, intent={}, amount={} {}",
                tenantId, intentId, amount, currency);

        return entry;
    }

    /**
     * Post a refund: Debit REFUNDS_PAYABLE, Credit PATIENT_CASH.
     */
    @Transactional
    public LedgerEntryEntity postRefund(UUID tenantId, String intentId, BigDecimal amount, String currency) {
        ensureAccounts(tenantId);

        LedgerAccountEntity debit = resolveAccount(tenantId, "REFUNDS_PAYABLE");
        LedgerAccountEntity credit = resolveAccount(tenantId, "PATIENT_CASH");

        LedgerEntryEntity entry = createEntry(
                tenantId, intentId, "REFUND", intentId,
                debit.getAccountId(), credit.getAccountId(),
                amount, currency,
                "Refund issued for intent " + intentId
        );

        log.info("Posted refund ledger entry: tenant={}, intent={}, amount={} {}",
                tenantId, intentId, amount, currency);

        return entry;
    }

    /**
     * Post a settlement: Debit SETTLEMENT_PAYABLE, Credit FACILITY_REVENUE.
     */
    @Transactional
    public LedgerEntryEntity postSettlement(UUID tenantId, String settlementId, BigDecimal amount, String currency) {
        ensureAccounts(tenantId);

        LedgerAccountEntity debit = resolveAccount(tenantId, "SETTLEMENT_PAYABLE");
        LedgerAccountEntity credit = resolveAccount(tenantId, "FACILITY_REVENUE");

        LedgerEntryEntity entry = createEntry(
                tenantId, null, "SETTLEMENT", settlementId,
                debit.getAccountId(), credit.getAccountId(),
                amount, currency,
                "Settlement payout for period " + settlementId
        );

        log.info("Posted settlement ledger entry: tenant={}, settlement={}, amount={} {}",
                tenantId, settlementId, amount, currency);

        return entry;
    }

    /**
     * Post a platform fee: Debit FACILITY_REVENUE, Credit PLATFORM_FEES.
     */
    @Transactional
    public LedgerEntryEntity postPlatformFee(UUID tenantId, String intentId, BigDecimal amount, String currency) {
        ensureAccounts(tenantId);

        LedgerAccountEntity debit = resolveAccount(tenantId, "FACILITY_REVENUE");
        LedgerAccountEntity credit = resolveAccount(tenantId, "PLATFORM_FEES");

        LedgerEntryEntity entry = createEntry(
                tenantId, intentId, "PLATFORM_FEE", intentId,
                debit.getAccountId(), credit.getAccountId(),
                amount, currency,
                "Platform fee for intent " + intentId
        );

        log.info("Posted platform fee ledger entry: tenant={}, intent={}, amount={} {}",
                tenantId, intentId, amount, currency);

        return entry;
    }

    /**
     * List ledger entries for a tenant with pagination.
     */
    public Page<LedgerEntryEntity> getEntries(UUID tenantId, Pageable pageable) {
        return entryRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * Create a balanced debit/credit journal entry.
     */
    private LedgerEntryEntity createEntry(UUID tenantId,
                                          String intentId,
                                          String refType,
                                          String refId,
                                          String debitAccountId,
                                          String creditAccountId,
                                          BigDecimal amount,
                                          String currency,
                                          String description) {
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setEntryId(UlidGenerator.generate());
        entry.setTenantId(tenantId);
        entry.setIntentId(intentId);
        entry.setReferenceType(refType);
        entry.setReferenceId(refId);
        entry.setDebitAccount(debitAccountId);
        entry.setCreditAccount(creditAccountId);
        entry.setAmount(amount);
        entry.setCurrency(currency);
        entry.setDescription(description);

        entry = entryRepository.save(entry);

        publishEvent("LEDGER", entry.getEntryId(), "LEDGER_ENTRY_POSTED",
                Map.of(
                        "entryId", entry.getEntryId(),
                        "refType", refType,
                        "refId", refId,
                        "debitAccount", debitAccountId,
                        "creditAccount", creditAccountId,
                        "amount", amount.toPlainString(),
                        "currency", currency
                ),
                tenantId);

        return entry;
    }

    /**
     * Resolve a ledger account by tenant and name.
     */
    private LedgerAccountEntity resolveAccount(UUID tenantId, String name) {
        return accountRepository.findByTenantIdAndName(tenantId, name)
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger account not found: tenant=" + tenantId + ", name=" + name));
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
