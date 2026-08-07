package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletAccountEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletTransactionEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.WalletAccountRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.WalletTransactionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

@Service
public class WalletPlatformService {

    private static final Logger log = LoggerFactory.getLogger(WalletPlatformService.class);

    /**
     * Actor types permitted to move custodial wallet money. Credit mints balance,
     * so this must never be open to citizen/provider actors; reads stay open like
     * sibling platform endpoints. Permissive when actor type is unset (ext_authz
     * already gated the call), matching the estate idiom.
     */
    /**
     * Operating a custodial wallet.
     *
     * <p>Was {@code {SYSTEM, SYSTEM_ADMIN, FINANCE_ADMIN, FACILITY_FINANCE, PLATFORM_OPERATOR}},
     * of which only SYSTEM is emittable, and the check was skipped entirely when the actor type
     * was absent — "permissive when unset (ext_authz already gated the call)". Envoy routes only to
     * experience-bff and has no route to mushex, so ext_authz had not gated the call, and omitting
     * the header was the only way a human could move custodial money. Now fails closed.</p>
     */
    static final ActorTypeGuard.Duty OPERATE_CUSTODIAL_WALLET = new ActorTypeGuard.Duty(
            "operating a custodial wallet",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            Set.of("SYSTEM_ADMIN", "FINANCE_ADMIN", "FACILITY_FINANCE", "PLATFORM_OPERATOR"));

    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WalletPlatformService(WalletAccountRepository walletAccountRepository,
                                 WalletTransactionRepository walletTransactionRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public List<WalletAccountEntity> listForOwner(String ownerRef) {
        TrustContext ctx = TrustContextHolder.require();
        return walletAccountRepository.findByTenantIdAndOwnerRef(ctx.tenantId(), ownerRef);
    }

    public WalletAccountEntity getWallet(String walletId) {
        TrustContext ctx = TrustContextHolder.require();
        return loadWallet(walletId, ctx.tenantId());
    }

    @Transactional
    public WalletAccountEntity createWallet(String ownerType, String ownerRef, String currency, String walletType) {
        TrustContext ctx = TrustContextHolder.require();
        WalletAccountEntity w = new WalletAccountEntity();
        w.setWalletId(UlidGenerator.generate());
        w.setTenantId(ctx.tenantId());
        w.setWalletCode("WLT-" + w.getWalletId().substring(0, 10).toUpperCase());
        w.setOwnerType(ownerType);
        w.setOwnerRef(ownerRef);
        w.setWalletType(walletType != null ? walletType : "STANDARD");
        w.setCurrency(currency != null ? currency : "USD");
        w.setStatus("ACTIVE");
        w.setAvailableBalance(BigDecimal.ZERO);
        w.setLedgerBalance(BigDecimal.ZERO);
        w = walletAccountRepository.save(w);

        publishEvent("WALLET", w.getWalletId(), "WALLET_CREATED",
                Map.of("walletId", w.getWalletId(), "ownerType", ownerType, "ownerRef", ownerRef,
                        "currency", w.getCurrency()),
                ctx.tenantId());
        log.info("Created MusheX wallet {} for owner {}/{}", w.getWalletId(), ownerType, ownerRef);
        return w;
    }

    @Transactional
    public WalletTransactionEntity credit(String walletId, BigDecimal amount, String transactionType,
                                          String referenceCode) {
        return postMovement(walletId, amount, transactionType, referenceCode, "CREDIT");
    }

    @Transactional
    public WalletTransactionEntity debit(String walletId, BigDecimal amount, String transactionType,
                                         String referenceCode) {
        return postMovement(walletId, amount, transactionType, referenceCode, "DEBIT");
    }

    private WalletTransactionEntity postMovement(String walletId, BigDecimal amount, String transactionType,
                                                   String referenceCode, String direction) {
        TrustContext ctx = TrustContextHolder.require();
        assertMoneyRole(ctx, direction);
        WalletAccountEntity w = loadWallet(walletId, ctx.tenantId());
        if ("DEBIT".equals(direction) && w.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient wallet balance");
        }
        WalletTransactionEntity txn = new WalletTransactionEntity();
        txn.setTxnId(UlidGenerator.generate());
        txn.setTenantId(ctx.tenantId());
        txn.setWalletId(walletId);
        txn.setTransactionType(transactionType != null ? transactionType : "ADJUSTMENT");
        txn.setAmount(amount);
        txn.setCurrency(w.getCurrency());
        txn.setDirection(direction);
        txn.setReferenceCode(referenceCode);
        walletTransactionRepository.save(txn);

        if ("CREDIT".equals(direction)) {
            w.setAvailableBalance(w.getAvailableBalance().add(amount));
            w.setLedgerBalance(w.getLedgerBalance().add(amount));
        } else {
            w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
            w.setLedgerBalance(w.getLedgerBalance().subtract(amount));
        }
        walletAccountRepository.save(w);

        publishEvent("WALLET_TXN", txn.getTxnId(), "WALLET_TRANSACTION_RECORDED",
                Map.of("walletId", walletId, "direction", direction, "amount", amount,
                        "transactionType", txn.getTransactionType()),
                ctx.tenantId());
        return txn;
    }

    public List<WalletTransactionEntity> listTransactions(String walletId) {
        TrustContext ctx = TrustContextHolder.require();
        loadWallet(walletId, ctx.tenantId());
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }

    private void assertMoneyRole(TrustContext ctx, String action) {
        ActorTypeGuard.require(ctx, OPERATE_CUSTODIAL_WALLET);
    }

    private WalletAccountEntity loadWallet(String walletId, UUID tenantId) {
        WalletAccountEntity w = walletAccountRepository.findById(walletId)
                .orElseThrow(() -> new PlatformResourceNotFoundException("Wallet not found: " + walletId));
        if (!w.getTenantId().equals(tenantId)) {
            throw new PlatformResourceNotFoundException("Wallet not found: " + walletId);
        }
        return w;
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType,
                              Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write MusheX outbox event {}", eventType, e);
        }
    }
}
