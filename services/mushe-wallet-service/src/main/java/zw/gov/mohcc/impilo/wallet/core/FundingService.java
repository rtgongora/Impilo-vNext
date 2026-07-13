package zw.gov.mohcc.impilo.wallet.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.DepositIntentEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.FundingSourceEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.DepositIntentRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.FundingSourceRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Funding source management and deposit operations.
 *
 * <p>Collection-intent truth: a deposit from an EXTERNAL source (bank account,
 * mobile money, remittance, card) creates a PENDING {@link DepositIntentEntity}
 * and credits the wallet ONLY when the money's arrival is confirmed — a
 * provider collection callback, or a matched line on the collection account's
 * bank statement. The former behavior credited the ledger immediately with no
 * external money movement ("deposit from EcoCash" without EcoCash being
 * charged). Cash deposits at a cashier desk remain immediate — the money is
 * physically in hand — but are recorded as CONFIRMED intents for the
 * reconciliation trail.</p>
 */
@Service
public class FundingService {

    private static final Logger log = LoggerFactory.getLogger(FundingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final FundingSourceRepository fundingSourceRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final DepositIntentRepository depositIntentRepository;

    public FundingService(FundingSourceRepository fundingSourceRepository,
                          WalletRepository walletRepository,
                          WalletService walletService,
                          DepositIntentRepository depositIntentRepository) {
        this.fundingSourceRepository = fundingSourceRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.depositIntentRepository = depositIntentRepository;
    }

    /**
     * Adds a new funding source (bank account, mobile money, etc.) to a wallet.
     */
    @Transactional
    public FundingSourceEntity addFundingSource(UUID walletId,
                                                 String sourceType,
                                                 String provider,
                                                 String accountRef,
                                                 String accountName) {
        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));

        FundingSourceEntity source = new FundingSourceEntity();
        source.setTenantId(wallet.getTenantId());
        source.setWalletId(walletId);
        source.setSourceType(sourceType);
        source.setProvider(provider);
        source.setAccountRef(accountRef);
        source.setAccountName(accountName);

        source = fundingSourceRepository.save(source);

        log.info("Added funding source: sourceId={} walletId={} type={} provider={}",
                source.getSourceId(), walletId, sourceType, provider);

        return source;
    }

    /**
     * Lists all funding sources for a wallet.
     */
    @Transactional(readOnly = true)
    public List<FundingSourceEntity> listFundingSources(UUID walletId) {
        return fundingSourceRepository.findByWalletId(walletId);
    }

    /**
     * Requests a deposit from a linked EXTERNAL funding source. Creates a
     * PENDING deposit intent with a short reference code the depositor quotes
     * (ZIPIT narrative / provider reference). The wallet is credited only by
     * {@link #confirmDeposit} — never here.
     */
    @Transactional
    public DepositIntentEntity requestDepositFromSource(UUID walletId,
                                                        UUID sourceId,
                                                        BigDecimal amount,
                                                        String reference) {
        FundingSourceEntity source = fundingSourceRepository.findBySourceId(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Funding source not found: " + sourceId));

        if (!source.getWalletId().equals(walletId)) {
            throw new IllegalArgumentException("Funding source does not belong to wallet: " + walletId);
        }
        if (!"ACTIVE".equals(source.getStatus())) {
            throw new IllegalStateException("Funding source is not active: status=" + source.getStatus());
        }
        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));

        DepositIntentEntity intent = new DepositIntentEntity();
        intent.setTenantId(wallet.getTenantId());
        intent.setWalletId(walletId);
        intent.setSourceId(sourceId);
        intent.setChannel(resolveChannel(source.getSourceType()));
        intent.setAmount(amount);
        intent.setCurrency(wallet.getCurrency() != null ? wallet.getCurrency() : "USD");
        intent.setReferenceCode(newReferenceCode());
        intent.setExternalRef(reference);
        intent = depositIntentRepository.save(intent);

        log.info("Deposit REQUESTED (pending external confirmation): depositId={} walletId={} "
                        + "amount={} channel={} referenceCode={}",
                intent.getDepositId(), walletId, amount, intent.getChannel(), intent.getReferenceCode());
        return intent;
    }

    /**
     * Confirms that a requested deposit's money has ARRIVED (provider callback
     * or matched statement line) and credits the wallet. Idempotent: an
     * already-confirmed deposit returns unchanged; the wallet credit itself is
     * idempotency-keyed per deposit so a replay can never double-credit.
     */
    @Transactional
    public DepositIntentEntity confirmDeposit(UUID depositId, String externalRef, String confirmedBy) {
        DepositIntentEntity intent = depositIntentRepository.findByDepositId(depositId)
                .orElseThrow(() -> new NoSuchElementException("Deposit intent not found: " + depositId));
        return confirm(intent, externalRef, confirmedBy);
    }

    /**
     * Confirms a pending deposit located by its reference code — the statement
     * matching entry point (a ZIPIT line carrying the code in its narrative).
     */
    @Transactional
    public DepositIntentEntity confirmDepositByReference(String referenceCode, String externalRef,
                                                         String confirmedBy) {
        DepositIntentEntity intent = depositIntentRepository.findByReferenceCode(referenceCode)
                .orElseThrow(() -> new NoSuchElementException(
                        "No deposit intent with reference code: " + referenceCode));
        return confirm(intent, externalRef, confirmedBy);
    }

    private DepositIntentEntity confirm(DepositIntentEntity intent, String externalRef, String confirmedBy) {
        if (DepositIntentEntity.STATUS_CONFIRMED.equals(intent.getStatus())) {
            log.info("Deposit {} already confirmed — idempotent return", intent.getDepositId());
            return intent;
        }
        if (!DepositIntentEntity.STATUS_PENDING.equals(intent.getStatus())) {
            throw new IllegalStateException("Deposit " + intent.getDepositId()
                    + " cannot be confirmed from status " + intent.getStatus());
        }

        TransactionEntity txn = walletService.credit(
                intent.getWalletId(),
                intent.getAmount(),
                "DEPOSIT",
                intent.getChannel(),
                intent.getReferenceCode(),
                "Confirmed deposit " + intent.getReferenceCode()
                        + (externalRef != null ? " (" + externalRef + ")" : ""),
                externalRef,
                null,
                null,
                "deposit:" + intent.getDepositId());

        intent.setStatus(DepositIntentEntity.STATUS_CONFIRMED);
        intent.setExternalRef(externalRef != null ? externalRef : intent.getExternalRef());
        intent.setConfirmedAt(OffsetDateTime.now());
        intent.setConfirmedBy(confirmedBy);
        intent.setCreditedTxnId(txn.getTxnId());
        intent = depositIntentRepository.save(intent);

        log.info("Deposit CONFIRMED and credited: depositId={} walletId={} amount={} txnId={}",
                intent.getDepositId(), intent.getWalletId(), intent.getAmount(), txn.getTxnId());
        return intent;
    }

    /** Cancels a still-pending deposit request. */
    @Transactional
    public DepositIntentEntity cancelDeposit(UUID depositId, String cancelledBy) {
        DepositIntentEntity intent = depositIntentRepository.findByDepositId(depositId)
                .orElseThrow(() -> new NoSuchElementException("Deposit intent not found: " + depositId));
        if (!DepositIntentEntity.STATUS_PENDING.equals(intent.getStatus())) {
            throw new IllegalStateException("Deposit " + intent.getDepositId()
                    + " cannot be cancelled from status " + intent.getStatus());
        }
        intent.setStatus(DepositIntentEntity.STATUS_CANCELLED);
        intent.setConfirmedBy(cancelledBy);
        return depositIntentRepository.save(intent);
    }

    @Transactional(readOnly = true)
    public List<DepositIntentEntity> listDeposits(UUID walletId) {
        return depositIntentRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }

    /**
     * Deposits cash at a facility cashier desk into a wallet. Immediate credit —
     * the money is physically in hand — recorded as a CONFIRMED intent for the
     * cash-up reconciliation trail.
     */
    @Transactional
    public TransactionEntity depositCash(UUID walletId,
                                          String cashierId,
                                          UUID facilityId,
                                          BigDecimal amount,
                                          String reference) {
        log.info("Cash deposit: walletId={} cashierId={} facilityId={} amount={}",
                walletId, cashierId, facilityId, amount);

        WalletEntity wallet = walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));

        TransactionEntity txn = walletService.credit(
                walletId,
                amount,
                "CASH_DEPOSIT",
                "CASH_DEPOSIT",
                reference,
                "Cash deposit at facility " + facilityId + " by cashier " + cashierId,
                cashierId,
                "CASHIER:" + facilityId,
                null,
                null);

        DepositIntentEntity trail = new DepositIntentEntity();
        trail.setTenantId(wallet.getTenantId());
        trail.setWalletId(walletId);
        trail.setChannel("CASH");
        trail.setAmount(amount);
        trail.setCurrency(wallet.getCurrency() != null ? wallet.getCurrency() : "USD");
        trail.setReferenceCode(newReferenceCode());
        trail.setExternalRef(reference);
        trail.setStatus(DepositIntentEntity.STATUS_CONFIRMED);
        trail.setConfirmedAt(OffsetDateTime.now());
        trail.setConfirmedBy(cashierId);
        trail.setCreditedTxnId(txn.getTxnId());
        depositIntentRepository.save(trail);

        return txn;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /** Short human-typable reference the depositor quotes (ZIPIT narrative). */
    private String newReferenceCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("IMP-");
            for (int i = 0; i < 8; i++) {
                sb.append(REF_ALPHABET[RANDOM.nextInt(REF_ALPHABET.length)]);
            }
            String code = sb.toString();
            if (depositIntentRepository.findByReferenceCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique deposit reference code");
    }

    private String resolveChannel(String sourceType) {
        if (sourceType == null) {
            return "BANK_ZIPIT";
        }
        return switch (sourceType.toUpperCase()) {
            case "MOBILE_MONEY" -> "MOBILE_MONEY";
            case "BANK_ACCOUNT" -> "BANK_ZIPIT";
            case "REMITTANCE" -> "REMITTANCE";
            case "CARD" -> "CARD";
            default -> "BANK_ZIPIT";
        };
    }
}
