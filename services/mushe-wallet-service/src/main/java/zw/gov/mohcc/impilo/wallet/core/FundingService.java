package zw.gov.mohcc.impilo.wallet.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.FundingSourceEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.FundingSourceRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Funding source management and deposit operations.
 *
 * A funding source represents an external account (bank account, mobile money,
 * remittance channel) linked to a wallet for loading funds. Cash deposits at
 * facility cashier desks are also supported.
 */
@Service
public class FundingService {

    private static final Logger log = LoggerFactory.getLogger(FundingService.class);

    private final FundingSourceRepository fundingSourceRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;

    public FundingService(FundingSourceRepository fundingSourceRepository,
                          WalletRepository walletRepository,
                          WalletService walletService) {
        this.fundingSourceRepository = fundingSourceRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
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
     * Deposits funds into a wallet from a linked funding source. The source must
     * exist and be ACTIVE.
     *
     * @return the credit transaction
     */
    @Transactional
    public TransactionEntity depositFromSource(UUID walletId,
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

        String channel = resolveChannel(source.getSourceType());

        log.info("Depositing from source: walletId={} sourceId={} amount={} type={}",
                walletId, sourceId, amount, source.getSourceType());

        return walletService.credit(
                walletId,
                amount,
                "DEPOSIT",
                channel,
                reference,
                "Deposit from " + source.getProvider() + " (" + source.getSourceType() + ")",
                source.getAccountRef(),
                source.getProvider(),
                null,
                null);
    }

    /**
     * Deposits cash at a facility cashier desk into a wallet.
     *
     * @return the credit transaction
     */
    @Transactional
    public TransactionEntity depositCash(UUID walletId,
                                          String cashierId,
                                          UUID facilityId,
                                          BigDecimal amount,
                                          String reference) {
        log.info("Cash deposit: walletId={} cashierId={} facilityId={} amount={}",
                walletId, cashierId, facilityId, amount);

        return walletService.credit(
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
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String resolveChannel(String sourceType) {
        if (sourceType == null) {
            return "BANK_TRANSFER";
        }
        return switch (sourceType.toUpperCase()) {
            case "MOBILE_MONEY" -> "MOBILE_MONEY";
            case "BANK_ACCOUNT" -> "BANK_TRANSFER";
            case "REMITTANCE" -> "BANK_TRANSFER";
            default -> "BANK_TRANSFER";
        };
    }
}
