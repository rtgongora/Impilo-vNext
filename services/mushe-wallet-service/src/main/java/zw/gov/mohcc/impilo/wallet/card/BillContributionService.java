package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import zw.gov.mohcc.impilo.wallet.core.FundingService;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.DepositIntentEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionRequestEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
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
    private final FundingService fundingService;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public BillContributionService(BillContributionRequestRepository requestRepository,
                                   BillContributionRepository contributionRepository,
                                   WalletService walletService,
                                   FundingService fundingService,
                                   EventOutboxRepository eventOutboxRepository,
                                   ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.contributionRepository = contributionRepository;
        this.walletService = walletService;
        this.fundingService = fundingService;
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

    /**
     * PSP donation entry point: a donor without a Mushe wallet pays through an external PSP
     * channel (EcoCash/OneMoney/card/bank via the Paynow aggregator). Creates a PENDING deposit
     * intent against the campaign's ESCROW wallet — purpose {@code CROWDFUNDING},
     * purpose_ref = shareToken, donor context in metadata — and returns it with the quotable
     * reference code. The escrow is credited and the contribution recorded ONLY when the money's
     * arrival is confirmed ({@link FundingService} confirm path: PSP callback or statement
     * match), which then calls {@link #recordSettledContribution}.
     *
     * <p>SEAM (documented, not faked): PSP collection INITIATION (the Paynow protocol hop) lives
     * in mushex-service ({@code PaynowGatewayAdapter}/{@code PaynowClient}); mushe has no client
     * to it. Callers initiate collection out-of-band (or the depositor quotes the reference code
     * in the transfer narrative for statement matching); this method never pretends a PSP call
     * happened.</p>
     */
    @Transactional
    public DepositIntentEntity pspDonate(String shareToken, BigDecimal amount, String currency,
                                         String channel, String contributorName, String message,
                                         boolean isAnonymous) {
        if (amount == null || amount.signum() <= 0) {
            throw new ContributionRejected("Donation amount must be positive");
        }
        BillContributionRequestEntity request = getByToken(shareToken);
        if (!BillContributionRequestEntity.ORIGIN_CAMPAIGN.equals(request.getOrigin())) {
            throw new ContributionRejected("PSP donations are only available for campaign requests");
        }
        if (!"OPEN".equals(request.getStatus())) {
            throw new ContributionRejected("This contribution request is " + request.getStatus().toLowerCase());
        }
        if (currency != null && !currency.isBlank() && !currency.equals(request.getCurrency())) {
            throw new ContributionRejected("Donation currency " + currency
                    + " does not match the campaign currency " + request.getCurrency());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (contributorName != null && !contributorName.isBlank()) {
            metadata.put("contributorName", contributorName);
        }
        if (message != null && !message.isBlank()) {
            metadata.put("message", message);
        }
        metadata.put("isAnonymous", isAnonymous);
        metadata.put("shareToken", shareToken);

        return fundingService.requestPurposedDeposit(request.getBeneficiaryWalletId(), channel,
                amount, request.getCurrency(), DepositIntentEntity.PURPOSE_CROWDFUNDING,
                shareToken, toJson(metadata), null);
    }

    /**
     * Called by {@link FundingService} inside the deposit-confirm transaction after a
     * CROWDFUNDING intent's credit has landed in the escrow wallet: records the contribution row
     * (origin PSP — NO second wallet credit), advances the raised total, and emits
     * {@code wallet.bill_contribution.recorded}. Exactly once per intent — idempotency key
     * {@code 'deposit:' + depositId} (UNIQUE on bill_contributions) guards replays.
     */
    @Transactional
    public BillContributionEntity recordSettledContribution(DepositIntentEntity intent) {
        String idem = "deposit:" + intent.getDepositId();
        var existing = contributionRepository.findByIdempotencyKey(idem);
        if (existing.isPresent()) {
            return existing.get();
        }
        BillContributionRequestEntity request = requestRepository.findByShareToken(intent.getPurposeRef())
                .orElseThrow(() -> new IllegalStateException(
                        "CROWDFUNDING deposit " + intent.getDepositId()
                                + " has no contribution request for purposeRef=" + intent.getPurposeRef()));

        String contributorName = null;
        String contributorRef = null;
        String message = null;
        boolean isAnonymous = false;
        if (intent.getMetadata() != null) {
            try {
                JsonNode meta = objectMapper.readTree(intent.getMetadata());
                contributorName = meta.hasNonNull("contributorName") ? meta.get("contributorName").asText() : null;
                // A person anchor (CPID) if the case layer supplied one; a PSP/bank txn ref is
                // NOT a person and must not masquerade as one (refunds would mint junk wallets).
                contributorRef = meta.hasNonNull("contributorRef") ? meta.get("contributorRef").asText() : null;
                message = meta.hasNonNull("message") ? meta.get("message").asText() : null;
                isAnonymous = meta.path("isAnonymous").asBoolean(false);
            } catch (Exception e) {
                log.warn("Unparseable metadata on deposit {} — recording contribution without donor context",
                        intent.getDepositId(), e);
            }
        }

        BillContributionEntity c = new BillContributionEntity();
        c.setId(UUID.randomUUID());
        c.setRequestId(request.getId());
        c.setAmount(intent.getAmount());
        c.setCurrency(intent.getCurrency());
        c.setOrigin(BillContributionEntity.ORIGIN_PSP);
        c.setContributorRef(contributorRef);
        c.setContributorName(contributorName);
        c.setMessage(message);
        c.setIdempotencyKey(idem);
        c = contributionRepository.save(c);

        advanceRaised(request, intent.getAmount());
        emitContributionEvent(EVENT_RECORDED, request, c, intent.getAmount(), isAnonymous, idem);
        log.info("PSP contribution {} settled onto request {} via deposit {} (raised {}/{})",
                intent.getAmount(), request.getId(), intent.getDepositId(),
                request.getRaisedAmount(), request.getTargetAmount());
        return c;
    }

    /**
     * Release escrowed campaign funds to the real beneficiary
     * ({@code beneficiaryTargetWalletId}). {@code amount == null} releases the full current
     * escrow balance. Only CAMPAIGN requests that are not cancelled can release; mushe enforces
     * the mechanical invariants (no over-release beyond the escrow balance, positive amount) —
     * the case layer (Daidzai) gates on campaign governance (verified-ACTIVE) before calling.
     * Emits {@code wallet.bill_contribution.released}.
     *
     * @return the escrow-side debit transaction
     */
    @Transactional
    public TransactionEntity release(UUID requestId, BigDecimal amount, String idempotencyKey,
                                     String releasedBy) {
        BillContributionRequestEntity request = getById(requestId);
        if (!BillContributionRequestEntity.ORIGIN_CAMPAIGN.equals(request.getOrigin())) {
            throw new ContributionRejected("Only campaign contribution requests can release escrow");
        }
        if (request.getStatus() != null && request.getStatus().startsWith("CANCELLED")) {
            throw new ContributionRejected("This contribution request is cancelled — funds are refunding");
        }
        if (request.getBeneficiaryTargetWalletId() == null) {
            throw new ContributionRejected("Campaign request has no beneficiary target wallet");
        }
        BigDecimal escrowBalance = walletService.getBalance(request.getBeneficiaryWalletId());
        BigDecimal toRelease = amount != null ? amount : escrowBalance;
        if (toRelease.signum() <= 0) {
            throw new ContributionRejected("Release amount must be positive (escrow balance is "
                    + escrowBalance.toPlainString() + ")");
        }
        if (toRelease.compareTo(escrowBalance) > 0) {
            throw new ContributionRejected("Cannot release " + toRelease.toPlainString()
                    + " — escrow balance is only " + escrowBalance.toPlainString());
        }

        String idem = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey : "release:" + requestId + ":" + UUID.randomUUID();
        TransactionEntity debitTxn = walletService.transfer(request.getBeneficiaryWalletId(),
                request.getBeneficiaryTargetWalletId(), toRelease, request.getShareToken(),
                "Crowdfund release — " + request.getTitle(), "SOCIAL_FUNDING", null, idem);

        request.setUpdatedAt(OffsetDateTime.now());
        requestRepository.save(request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.getId().toString());
        if (request.getCampaignRef() != null) {
            payload.put("campaignRef", request.getCampaignRef().toString());
        }
        payload.put("amount", toRelease.toPlainString());
        payload.put("currency", request.getCurrency());
        payload.put("idempotencyKey", idem);
        if (releasedBy != null) {
            payload.put("releasedBy", releasedBy);
        }
        emitEvent(EVENT_RELEASED, request, request.getId().toString(), idem, payload);
        log.info("Crowdfund release {} from escrow {} to beneficiary {} (request {})", toRelease,
                request.getBeneficiaryWalletId(), request.getBeneficiaryTargetWalletId(), requestId);
        return debitTxn;
    }

    /**
     * Cancel a campaign and refund its contributions from the escrow wallet — a batched,
     * resumable engine, newest-first:
     * <ul>
     *   <li>WALLET origin — transfer escrow → contributor wallet, idempotency key
     *       {@code 'refund:' + contributionId} → REFUNDED.</li>
     *   <li>PSP / CASH_ASSISTED with a resolvable person ref — resolve-or-create the donor's
     *       INDIVIDUAL wallet and credit-as-refund (escrow transfer) → REFUNDED.</li>
     *   <li>No resolvable donor — transfer to the per-tenant UNCLAIMED_DONATIONS system wallet
     *       (ownerType {@code SYSTEM_UNCLAIMED}) → UNCLAIMED.</li>
     *   <li>Escrow cannot cover the row (funds already released) — REFUND_PENDING with the
     *       shortfall logged; the escrow never goes negative and nothing is invented. A re-run
     *       retries only PENDING/unprocessed rows (terminal rows are skipped; every transfer is
     *       idempotency-keyed).</li>
     * </ul>
     * Emits {@code wallet.bill_contribution.refunded} per refunded/unclaimed row. The request
     * ends {@code CANCELLED_SETTLED} when no REFUND_PENDING remain, else
     * {@code CANCELLED_REFUNDING}. Calling on an already-settled cancellation is a no-op.
     */
    @Transactional
    public BillContributionRequestEntity cancelAndRefund(UUID requestId, String cancelledBy) {
        BillContributionRequestEntity request = getById(requestId);
        if (!BillContributionRequestEntity.ORIGIN_CAMPAIGN.equals(request.getOrigin())) {
            throw new ContributionRejected("Only campaign contribution requests can cancel-and-refund");
        }
        if (BillContributionRequestEntity.STATUS_CANCELLED_SETTLED.equals(request.getStatus())) {
            return request; // idempotent replay — everything already refunded
        }
        UUID escrowWalletId = request.getBeneficiaryWalletId();

        boolean anyPending = false;
        List<BillContributionEntity> rows =
                contributionRepository.findByRequestIdOrderByCreatedAtDesc(request.getId());
        for (BillContributionEntity c : rows) {
            if (BillContributionEntity.REFUND_REFUNDED.equals(c.getRefundStatus())
                    || BillContributionEntity.REFUND_UNCLAIMED.equals(c.getRefundStatus())) {
                continue; // terminal — never refund twice
            }
            BigDecimal escrowBalance = walletService.getBalance(escrowWalletId);
            if (escrowBalance.compareTo(c.getAmount()) < 0) {
                // Honest shortfall: the money left escrow (released) — hold, never go negative.
                c.setRefundStatus(BillContributionEntity.REFUND_PENDING);
                contributionRepository.save(c);
                anyPending = true;
                log.warn("Refund shortfall on contribution {}: escrow balance {} < {} — held REFUND_PENDING",
                        c.getId(), escrowBalance, c.getAmount());
                continue;
            }

            UUID destinationWalletId;
            String outcome;
            if (c.getContributorWalletId() != null) {
                destinationWalletId = c.getContributorWalletId();
                outcome = BillContributionEntity.REFUND_REFUNDED;
            } else if (c.getContributorRef() != null && !c.getContributorRef().isBlank()) {
                WalletEntity donorWallet = walletService.createWallet(request.getTenantId(),
                        "INDIVIDUAL", c.getContributorRef(),
                        c.getContributorName() != null ? c.getContributorName() : c.getContributorRef(),
                        request.getCurrency()); // get-or-create by owner
                destinationWalletId = donorWallet.getWalletId();
                outcome = BillContributionEntity.REFUND_REFUNDED;
            } else {
                WalletEntity unclaimed = walletService.createWallet(request.getTenantId(),
                        "SYSTEM_UNCLAIMED", "UNCLAIMED_DONATIONS",
                        "Unclaimed crowdfund donations", request.getCurrency());
                destinationWalletId = unclaimed.getWalletId();
                outcome = BillContributionEntity.REFUND_UNCLAIMED;
            }

            TransactionEntity debitTxn = walletService.transfer(escrowWalletId, destinationWalletId,
                    c.getAmount(), request.getShareToken(),
                    "Crowdfund refund — " + request.getTitle(), "SOCIAL_FUNDING", null,
                    "refund:" + c.getId());
            c.setRefundStatus(outcome);
            c.setRefundTxnId(debitTxn.getTxnId());
            contributionRepository.save(c);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getId().toString());
            if (request.getCampaignRef() != null) {
                payload.put("campaignRef", request.getCampaignRef().toString());
            }
            payload.put("contributionId", c.getId().toString());
            if (c.getContributorRef() != null) {
                payload.put("contributorRef", c.getContributorRef());
            }
            payload.put("amount", c.getAmount().toPlainString());
            payload.put("currency", request.getCurrency());
            payload.put("refundOutcome", outcome);
            payload.put("idempotencyKey", "refund:" + c.getId());
            emitEvent(EVENT_REFUNDED, request, c.getId().toString(), "refund:" + c.getId(), payload);
        }

        request.setStatus(anyPending
                ? BillContributionRequestEntity.STATUS_CANCELLED_REFUNDING
                : BillContributionRequestEntity.STATUS_CANCELLED_SETTLED);
        request.setUpdatedAt(OffsetDateTime.now());
        requestRepository.save(request);
        log.info("Campaign request {} cancelled by {} — refunds {}", requestId, cancelledBy,
                request.getStatus());
        return request;
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
