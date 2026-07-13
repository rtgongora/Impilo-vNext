package zw.gov.mohcc.impilo.wallet.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionRequestEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRequestRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Mushe social funding (unified card, Phase 3 feature): a shareable "help pay my bill" link a person
 * or group can chip in toward. Each contribution credits the beneficiary's wallet (the money SoR,
 * idempotently) and advances the raised total; the request auto-fulfils when the target is reached.
 */
@Service
public class BillContributionService {

    private static final Logger log = LoggerFactory.getLogger(BillContributionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BillContributionRequestRepository requestRepository;
    private final BillContributionRepository contributionRepository;
    private final WalletService walletService;

    public BillContributionService(BillContributionRequestRepository requestRepository,
                                   BillContributionRepository contributionRepository,
                                   WalletService walletService) {
        this.requestRepository = requestRepository;
        this.contributionRepository = contributionRepository;
        this.walletService = walletService;
    }

    public static class ContributionRejected extends RuntimeException {
        public ContributionRejected(String message) { super(message); }
    }

    /** Create a shareable contribution request for the beneficiary's wallet; returns it with a token. */
    @Transactional
    public BillContributionRequestEntity createRequest(UUID tenantId, UUID beneficiaryWalletId, String title,
                                                       String billRef, BigDecimal targetAmount, String currency,
                                                       String createdBy) {
        BillContributionRequestEntity r = new BillContributionRequestEntity();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setShareToken(newToken());
        r.setBeneficiaryWalletId(beneficiaryWalletId);
        r.setTitle(title);
        r.setBillRef(billRef);
        r.setTargetAmount(targetAmount);
        r.setCurrency(currency != null && !currency.isBlank() ? currency : "USD");
        r.setCreatedBy(createdBy);
        return requestRepository.save(r);
    }

    @Transactional(readOnly = true)
    public BillContributionRequestEntity getByToken(String shareToken) {
        return requestRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ContributionRejected("Unknown contribution link"));
    }

    @Transactional(readOnly = true)
    public List<BillContributionEntity> contributions(UUID requestId) {
        return contributionRepository.findByRequestIdOrderByCreatedAtDesc(requestId);
    }

    /**
     * Contribute to a shared bill request: credits the beneficiary's wallet and records the
     * contribution. Idempotent on {@code idempotencyKey}. Rejects closed requests / non-positive amounts.
     */
    @Transactional
    public BillContributionEntity contribute(String shareToken, BigDecimal amount, String contributorRef,
                                             String contributorName, String message, String idempotencyKey) {
        if (amount == null || amount.signum() <= 0) {
            throw new ContributionRejected("Contribution amount must be positive");
        }
        BillContributionRequestEntity request = getByToken(shareToken);
        if (!"OPEN".equals(request.getStatus())) {
            throw new ContributionRejected("This contribution request is " + request.getStatus().toLowerCase());
        }

        String idem = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey : "billcontrib:" + UUID.randomUUID();
        var existing = contributionRepository.findByIdempotencyKey(idem);
        if (existing.isPresent()) {
            return existing.get(); // idempotent replay — no double credit
        }

        // Credit the beneficiary's wallet (money SoR, idempotent on the same key).
        walletService.credit(request.getBeneficiaryWalletId(), amount, "BILL_CONTRIBUTION",
                "SOCIAL_FUNDING", request.getShareToken(),
                "Bill contribution" + (contributorName != null ? " from " + contributorName : ""),
                contributorRef, contributorName, null, idem);

        BillContributionEntity c = new BillContributionEntity();
        c.setId(UUID.randomUUID());
        c.setRequestId(request.getId());
        c.setAmount(amount);
        c.setCurrency(request.getCurrency());
        c.setContributorRef(contributorRef);
        c.setContributorName(contributorName);
        c.setMessage(message);
        c.setIdempotencyKey(idem);
        c = contributionRepository.save(c);

        request.setRaisedAmount(request.getRaisedAmount().add(amount));
        if (request.getTargetAmount() != null && request.getRaisedAmount().compareTo(request.getTargetAmount()) >= 0) {
            request.setStatus("FULFILLED");
        }
        request.setUpdatedAt(OffsetDateTime.now());
        requestRepository.save(request);
        log.info("Bill contribution {} to request {} (raised {}/{})", amount, request.getId(),
                request.getRaisedAmount(), request.getTargetAmount());
        return c;
    }

    @Transactional
    public BillContributionRequestEntity close(UUID requestId) {
        BillContributionRequestEntity r = requestRepository.findById(requestId)
                .orElseThrow(() -> new ContributionRejected("Unknown contribution request"));
        r.setStatus("CLOSED");
        r.setUpdatedAt(OffsetDateTime.now());
        return requestRepository.save(r);
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
