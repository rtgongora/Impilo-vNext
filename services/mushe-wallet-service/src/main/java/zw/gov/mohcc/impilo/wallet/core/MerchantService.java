package zw.gov.mohcc.impilo.wallet.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.MerchantAccountEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.MerchantAccountRepository;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Merchant account lifecycle and payment operations.
 *
 * A merchant is a health provider (pharmacy, lab, imaging centre, etc.) that
 * receives payments through the Mushe wallet rail. Registering a merchant
 * creates both a MERCHANT-type wallet and a merchant account record.
 */
@Service
public class MerchantService {

    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantAccountRepository merchantAccountRepository;
    private final WalletService walletService;

    public MerchantService(MerchantAccountRepository merchantAccountRepository,
                           WalletService walletService) {
        this.merchantAccountRepository = merchantAccountRepository;
        this.walletService = walletService;
    }

    /**
     * Registers a new merchant by creating a MERCHANT wallet and a merchant account record.
     * If a merchant with the same provider number already exists for the tenant, returns
     * the existing merchant.
     */
    @Transactional
    public MerchantAccountEntity registerMerchant(UUID tenantId,
                                                   String providerNumber,
                                                   UUID facilityId,
                                                   String merchantName,
                                                   String merchantType,
                                                   String settlementAccount,
                                                   String settlementBank) {
        // Check if merchant already exists
        var existing = merchantAccountRepository.findByTenantIdAndProviderNumber(tenantId, providerNumber);
        if (existing.isPresent()) {
            log.info("Merchant already exists for tenant={} provider={}, returning existing merchantId={}",
                    tenantId, providerNumber, existing.get().getMerchantId());
            return existing.get();
        }

        // Create the merchant wallet
        WalletEntity wallet = walletService.createWallet(
                tenantId,
                "MERCHANT",
                providerNumber,
                merchantName,
                "ZWL");

        // Create the merchant account record
        MerchantAccountEntity merchant = new MerchantAccountEntity();
        merchant.setTenantId(tenantId);
        merchant.setWalletId(wallet.getWalletId());
        merchant.setProviderNumber(providerNumber);
        merchant.setFacilityId(facilityId);
        merchant.setMerchantName(merchantName);
        merchant.setMerchantType(merchantType != null ? merchantType : "HEALTH_PROVIDER");
        merchant.setSettlementAccount(settlementAccount);
        merchant.setSettlementBank(settlementBank);

        merchant = merchantAccountRepository.save(merchant);

        log.info("Registered merchant: merchantId={} provider={} walletId={}",
                merchant.getMerchantId(), providerNumber, wallet.getWalletId());

        return merchant;
    }

    /**
     * Retrieves a merchant account by its UUID.
     */
    @Transactional(readOnly = true)
    public MerchantAccountEntity getMerchant(UUID tenantId, UUID merchantId) {
        return merchantAccountRepository.findByMerchantId(merchantId)
                .filter(m -> m.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NoSuchElementException(
                        "Merchant not found: " + merchantId));
    }

    /**
     * Retrieves a merchant account by provider number within a tenant.
     */
    @Transactional(readOnly = true)
    public MerchantAccountEntity getMerchantByProviderNumber(UUID tenantId, String providerNumber) {
        return merchantAccountRepository.findByTenantIdAndProviderNumber(tenantId, providerNumber)
                .orElseThrow(() -> new NoSuchElementException(
                        "Merchant not found for provider: " + providerNumber));
    }

    /**
     * Pays a merchant by transferring funds from an individual wallet to the
     * merchant's wallet. Looks up the merchant by provider number and delegates
     * to {@link WalletService#transfer}.
     *
     * @return the debit transaction on the source (payer) wallet
     */
    @Transactional
    public TransactionEntity payMerchant(UUID tenantId,
                                         UUID fromWalletId,
                                         String merchantProviderNumber,
                                         BigDecimal amount,
                                         String reference,
                                         String channel,
                                         UUID correlationId,
                                         String idempotencyKey) {
        MerchantAccountEntity merchant = getMerchantByProviderNumber(tenantId, merchantProviderNumber);

        if (!"ACTIVE".equals(merchant.getStatus())) {
            throw new IllegalStateException("Merchant is not active: status=" + merchant.getStatus()
                    + " merchantId=" + merchant.getMerchantId());
        }

        log.info("Paying merchant: from={} merchant={} amount={} ref={}",
                fromWalletId, merchantProviderNumber, amount, reference);

        return walletService.transfer(
                fromWalletId,
                merchant.getWalletId(),
                amount,
                reference,
                "Payment to " + merchant.getMerchantName(),
                channel,
                correlationId,
                idempotencyKey);
    }
}
