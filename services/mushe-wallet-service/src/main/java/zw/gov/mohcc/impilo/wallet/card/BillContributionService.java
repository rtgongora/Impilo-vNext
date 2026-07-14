package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionRequestEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRequestRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mushe social funding (unified card, Phase 3 feature) and the crowdfunding money layer.
 *
 * <p>Two request origins:</p>
 * <ul>
 *   <li><b>BILL</b> — the original shareable "help pay my bill" link: contributions credit the
 *       beneficiary's wallet directly (idempotently) and advance the raised total.</li>
 *   <li><b>CAMPAIGN</b> — a crowdfunding campaign (case layer: Daidzai) backed 1:1 by this
 *       request. Contributions land in a dedicated ESCROW wallet
 *       (ownerType={@code CROWDFUND_ESCROW}, ownerRef=campaignRef); money only reaches the real
 *       beneficiary ({@code beneficiaryTargetWalletId}) through an explicit governed release.
 *       Wallet-funded contributions are an atomic donor-debit + escrow-credit transfer; the
 *       bare-credit path is reserved for CASH_ASSISTED (money physically in hand, flagged).</li>
 * </ul>
 *
 * <p>Every contribution emits {@code wallet.bill_contribution.recorded} on the outbox so the
 * case layer (Daidzai) can advance campaign progress without owning money truth.</p>
 */
@Service
public class BillContributionService {

    private static final Logger log = LoggerFactory.getLogger(BillContributionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Owner type of per-campaign escrow wallets (ownerRef = campaignRef). */
    public static final String ESCROW_OWNER_TYPE = "CROWDFUND_ESCROW";

    public static final String EVENT_RECORDED = "wallet.bill_contribution.recorded";
    public static final String EVENT_RELEASED = "wallet.bill_contribution.released";
    public static final String EVENT_REFUNDED = "wallet.bill_contribution.refunded";

    private final BillContributionRequestRepository requestRepository;
    private final BillContributionRepository contributionRepository;
    private final WalletService walletService;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public BillContributionService(BillContributionRequestRepository requestRepository,
                                   BillContributionRepository contributionRepository,
                                   WalletService walletService,
                                   EventOutboxRepository eventOutboxRepository,
                                   ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.contributionRepository = contributionRepository;
        this.walletService = walletService;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    public static class ContributionRejected extends RuntimeException {
        public ContributionRejected(String message) { super(message); }
    }

    /** Create a shareable contribution request for the beneficiary's wallet; returns it with a token. */
    @Transactional
    public BillContributionRequestEntity createRequest(UUID tenantId, UUID beneficiaryWalletId, String title,
                                                       String billRef, BigDecimal targetAmount, String currency,
                                                       String createdBy) {
        return createRequest(tenantId, beneficiaryWalletId, title, billRef, targetAmount, currency, createdBy,
                BillContributionRequestEntity.ORIGIN_BILL, null, null);
    }

    /**
     * Create a contribution request. For origin {@code CAMPAIGN}: {@code campaignRef} and
     * {@code beneficiaryTargetWalletId} (the real beneficiary) are required; the request's
     * {@code beneficiaryWalletId} is a dedicated escrow wallet — the given
     * {@code beneficiaryWalletId} if supplied (a pre-created escrow), otherwise one created here
     * keyed {@code CROWDFUND_ESCROW}:{campaignRef} (get-or-create, so replays are safe).
     */
    @Transactional
    public BillContributionRequestEntity createRequest(UUID tenantId, UUID beneficiaryWalletId, String title,
                                                       String billRef, BigDecimal targetAmount, String currency,
                                                       String createdBy, String origin, UUID campaignRef,
                                                       UUID beneficiaryTargetWalletId) {
        String cur = currency != null && !currency.isBlank() ? currency : "USD";
        String org = origin != null && !origin.isBlank() ? origin : BillContributionRequestEntity.ORIGIN_BILL;
        if (!BillContributionRequestEntity.ORIGIN_BILL.equals(org)
                && !BillContributionRequestEntity.ORIGIN_CAMPAIGN.equals(org)) {
            throw new ContributionRejected("Unknown contribution request origin: " + org);
        }

        BillContributionRequestEntity r = new BillContributionRequestEntity();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setShareToken(newToken());
        r.setOrigin(org);
        r.setTitle(title);
        r.setBillRef(billRef);
        r.setTargetAmount(targetAmount);
        r.setCurrency(cur);
        r.setCreatedBy(createdBy);

        if (BillContributionRequestEntity.ORIGIN_CAMPAIGN.equals(org)) {
            if (campaignRef == null) {
                throw new ContributionRejected("A campaign contribution request requires a campaignRef");
            }
            if (beneficiaryTargetWalletId == null) {
                throw new ContributionRejected(
                        "A campaign contribution request requires the real beneficiary's wallet"
                                + " (beneficiaryTargetWalletId)");
            }
            UUID escrowWalletId = beneficiaryWalletId; // caller-supplied pre-created escrow, if any
            if (escrowWalletId == null) {
                WalletEntity escrow = walletService.createWallet(tenantId, ESCROW_OWNER_TYPE,
                        campaignRef.toString(), "Crowdfund escrow — " + title, cur);
                escrowWalletId = escrow.getWalletId();
            }
            r.setBeneficiaryWalletId(escrowWalletId);
            r.setBeneficiaryTargetWalletId(beneficiaryTargetWalletId);
            r.setCampaignRef(campaignRef);
        } else {
            if (beneficiaryWalletId == null) {
                throw new ContributionRejected("A bill contribution request requires a beneficiary wallet");
            }
            r.setBeneficiaryWalletId(beneficiaryWalletId);
        }
        return requestRepository.save(r);
    }

    @Transactional(readOnly = true)
    public BillContributionRequestEntity getByToken(String shareToken) {
        return requestRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ContributionRejected("Unknown contribution link"));
    }

    @Transactional(readOnly = true)
    public BillContributionRequestEntity getById(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ContributionRejected("Unknown contribution request"));
    }

    @Transactional(readOnly = true)
    public List<BillContributionEntity> contributions(UUID requestId) {
        return contributionRepository.findByRequestIdOrderByCreatedAtDesc(requestId);
    }

    /** Legacy entry point: contribution without a contributor wallet (see the full overload). */
    @Transactional
    public BillContributionEntity contribute(String shareToken, BigDecimal amount, String contributorRef,
                                             String contributorName, String message, String idempotencyKey) {
        return contribute(shareToken, amount, contributorRef, contributorName, message, idempotencyKey,
                null, null, false);
    }

    /**
     * Contribute to a shared request. Idempotent on {@code idempotencyKey}; rejects closed
     * requests and non-positive amounts.
     *
     * <p>Money movement by shape:</p>
     * <ul>
     *   <li>{@code contributorWalletId} present — atomic donor-debit + credit of the request's
     *       receiving wallet (escrow for CAMPAIGN) via a single wallet transfer under the
     *       contribution's idempotency key. Insufficient donor funds reject cleanly (the whole
     *       transaction rolls back — no partial state).</li>
     *   <li>No wallet, {@code contributionOrigin=CASH_ASSISTED} — bare credit of the receiving
     *       wallet, recorded and flagged (cash physically in hand).</li>
     *   <li>No wallet on a plain BILL request — the original bare-credit behaviour, recorded as
     *       CASH_ASSISTED (money arrived off-system).</li>
     *   <li>No wallet on a CAMPAIGN request without the CASH_ASSISTED flag — rejected; campaign
     *       money must come from a real donor wallet, the PSP rail, or assisted cash.</li>
     * </ul>
     */
    @Transactional
    public BillContributionEntity contribute(String shareToken, BigDecimal amount, String contributorRef,
                                             String contributorName, String message, String idempotencyKey,
                                             UUID contributorWalletId, String contributionOrigin,
                                             boolean isAnonymous) {
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
            return existing.get(); // idempotent replay — no double credit/debit
        }

        boolean campaign = BillContributionRequestEntity.ORIGIN_CAMPAIGN.equals(request.getOrigin());
        String origin;
        if (contributorWalletId != null) {
            origin = BillContributionEntity.ORIGIN_WALLET;
            // Atomic donor-debit + receiving-wallet credit (money SoR, idempotent on the same key).
            try {
                walletService.transfer(contributorWalletId, request.getBeneficiaryWalletId(), amount,
                        request.getShareToken(),
                        "Bill contribution" + (contributorName != null ? " from " + contributorName : ""),
                        "SOCIAL_FUNDING", null, idem);
            } catch (IllegalStateException | IllegalArgumentException | java.util.NoSuchElementException e) {
                // Rolls the whole transaction back — clean rejection, no partial state.
                throw new ContributionRejected("Contribution could not be funded: " + e.getMessage());
            }
        } else if (BillContributionEntity.ORIGIN_CASH_ASSISTED.equals(contributionOrigin) || !campaign) {
            origin = BillContributionEntity.ORIGIN_CASH_ASSISTED;
            walletService.credit(request.getBeneficiaryWalletId(), amount, "BILL_CONTRIBUTION",
                    "SOCIAL_FUNDING", request.getShareToken(),
                    "Bill contribution" + (contributorName != null ? " from " + contributorName : ""),
                    contributorRef, contributorName, null, idem);
        } else {
            throw new ContributionRejected("Campaign contributions require a contributor wallet"
                    + " (or the PSP rail / a flagged CASH_ASSISTED recording)");
        }

        BillContributionEntity c = new BillContributionEntity();
        c.setId(UUID.randomUUID());
        c.setRequestId(request.getId());
        c.setAmount(amount);
        c.setCurrency(request.getCurrency());
        c.setContributorWalletId(contributorWalletId);
        c.setOrigin(origin);
        c.setContributorRef(contributorRef);
        c.setContributorName(contributorName);
        c.setMessage(message);
        c.setIdempotencyKey(idem);
        c = contributionRepository.save(c);

        advanceRaised(request, amount);
        emitContributionEvent(EVENT_RECORDED, request, c, amount, isAnonymous, idem);
        log.info("Bill contribution {} ({}) to request {} (raised {}/{})", amount, origin, request.getId(),
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

    // ── Internal helpers ────────────────────────────────────────────────────

    void advanceRaised(BillContributionRequestEntity request, BigDecimal amount) {
        request.setRaisedAmount(request.getRaisedAmount().add(amount));
        if (request.getTargetAmount() != null
                && request.getRaisedAmount().compareTo(request.getTargetAmount()) >= 0) {
            request.setStatus("FULFILLED");
        }
        request.setUpdatedAt(OffsetDateTime.now());
        requestRepository.save(request);
    }

    /** Contribution-lifecycle outbox event ({@value #EVENT_RECORDED} / refunded). Never nulls in the payload. */
    void emitContributionEvent(String eventType, BillContributionRequestEntity request,
                               BillContributionEntity contribution, BigDecimal amount,
                               Boolean isAnonymousHint, String moneyIdempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.getId().toString());
        if (request.getCampaignRef() != null) {
            payload.put("campaignRef", request.getCampaignRef().toString());
        }
        if (contribution != null) {
            payload.put("contributionId", contribution.getId().toString());
            if (contribution.getContributorRef() != null) {
                payload.put("contributorRef", contribution.getContributorRef());
            }
        }
        payload.put("amount", amount.toPlainString());
        payload.put("currency", request.getCurrency());
        if (isAnonymousHint != null) {
            payload.put("isAnonymousHint", isAnonymousHint);
        }
        if (moneyIdempotencyKey != null) {
            payload.put("idempotencyKey", moneyIdempotencyKey);
        }
        emitEvent(eventType, request,
                contribution != null ? contribution.getId().toString() : request.getId().toString(),
                moneyIdempotencyKey, payload);
    }

    void emitEvent(String eventType, BillContributionRequestEntity request, String aggregateId,
                   String dedupKey, Map<String, Object> payload) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("BILL_CONTRIBUTION");
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setTenantId(request.getTenantId());
            event.setSubjectId(request.getCampaignRef() != null
                    ? request.getCampaignRef().toString() : request.getId().toString());
            event.setSubjectType(request.getCampaignRef() != null ? "CAMPAIGN" : "BILL_CONTRIBUTION_REQUEST");
            event.setPartitionKey(request.getId().toString());
            event.setIdempotencyKey("mushe-wallet:" + eventType + ":"
                    + (dedupKey != null ? dedupKey : aggregateId + ":" + UUID.randomUUID()));
            event.setOccurredAt(OffsetDateTime.now());
            event.setPayloadJson(toJson(payload));
            eventOutboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write bill-contribution outbox event: eventType={}", eventType, e);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to serialize payload to JSON", e);
            return "{}";
        }
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
