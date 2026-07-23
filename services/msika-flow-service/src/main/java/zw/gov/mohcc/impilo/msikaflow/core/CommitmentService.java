package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.InventoryClient;
import zw.gov.mohcc.impilo.msikaflow.integration.MushexClient;
import zw.gov.mohcc.impilo.msikaflow.integration.OrosClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * OF-B6/OF-B11 (+ finance lane OF-B8/OF-B9/OF-B10) — the §8.9 commitment
 * orchestrator: revalidate offer (RC-2/RC-4) → eligibility recheck (RC-6) →
 * DURA conditional reserve, fail-close (RC-3) → OROS prescription claim (RC-7)
 * → financial clearance re-verification (step 7: COSTA charge + Ruvimbo
 * liability + PA posture, §8.7) → payment execution (step 8: one MusheX intent
 * per obligation, intent → PAID, §8.8) → controlled-register write (§13.4) →
 * COMMITTED → losing offers released → dispatch seam → events.
 *
 * <p>RC-1 rides the Idempotency-Key + single-active-selection invariant;
 * RC-5-style compensation releases the DURA holds, the OROS claim — and
 * refunds a PAID intent — on any later-step failure; RC-8 dispatch is
 * idempotent on the selection id.</p>
 *
 * <p>Step-8 doctrine (OF-B10, settled): a positive due-now shortfall opens a
 * real MusheX PaymentIntent and holds the selection at {@code AWAITING_PAYMENT};
 * the {@code mushex.payment.status.changed} callback resumes steps 9–12 on
 * PAID, or compensates on terminal failure; the reservation-TTL sweep is the
 * timeout backstop (§8.8.4 — the retry window IS the reservation TTL). NO
 * two-phase capture; escrow-on-PoD remains the documented OF-B17/OF-B18 seam.</p>
 */
@Service
public class CommitmentService {

    private static final Logger log = LoggerFactory.getLogger(CommitmentService.class);

    // Discrete coded outcomes (RC families)
    public static final String CODE_COMMITTED = "COMMITTED";
    public static final String CODE_OFFER_EXPIRED = "OFFER_EXPIRED";                 // RC-2
    public static final String CODE_OFFER_NOT_ACTIVE = "OFFER_NOT_ACTIVE";           // RC-2 family
    public static final String CODE_STOCK_UNAVAILABLE = "STOCK_UNAVAILABLE";         // RC-3
    public static final String CODE_STOCK_SOURCE_UNVERIFIED = "STOCK_SOURCE_UNVERIFIED"; // RC-3 (no DURA source)
    public static final String CODE_ELIGIBILITY_LAPSED = "ELIGIBILITY_LAPSED";       // RC-6
    public static final String CODE_CONTROLLED_VENDOR_UNAUTHORISED = "CONTROLLED_VENDOR_UNAUTHORISED"; // §13.4
    public static final String CODE_CLAIM_FAILED = "CLAIM_FAILED";                   // RC-7
    public static final String CODE_CONTROLLED_REGISTER_WRITE_FAILED = "CONTROLLED_REGISTER_WRITE_FAILED";
    // Finance lane (steps 7–8)
    public static final String CODE_FINANCIAL_UNVERIFIED = "FINANCIAL_UNVERIFIED";   // §10.4 degradation, fail-closed
    public static final String CODE_NOT_COVERED = "NOT_COVERED";                     // hard Ruvimbo refusal
    public static final String CODE_PA_REQUIRED = "PA_REQUIRED";                     // OF-B8 §8.7.6
    public static final String CODE_PAYMENT_INTENT_FAILED = "PAYMENT_INTENT_FAILED"; // step 8, intent never opened
    public static final String CODE_PAYMENT_FAILED = "PAYMENT_FAILED";               // terminal intent failure
    public static final String CODE_PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";             // reservation-TTL lapse
    public static final String CODE_AWAITING_PAYMENT = "AWAITING_PAYMENT";           // held, not an error

    public record CommitResult(SelectionEntity selection, boolean committed, String outcomeCode,
                               boolean replayed) {}

    private final MarketplaceRequestRepository requestRepository;
    private final FulfillmentOfferRepository offerRepository;
    private final FulfillmentOfferLineRepository offerLineRepository;
    private final SelectionRepository selectionRepository;
    private final VendorProfileRepository vendorRepository;
    private final ReservationRepository reservationRepository;
    private final EventOutboxRepository outboxRepository;
    private final EligibilityService eligibilityService;
    private final InventoryClient inventoryClient;
    private final OrosClient orosClient;
    private final OfferFinancialsService offerFinancialsService;
    private final MushexClient mushexClient;
    private final FulfilmentDispatchService fulfilmentDispatchService;
    private final ObjectMapper objectMapper;
    private final long defaultFulfillmentWindowHours;

    public CommitmentService(MarketplaceRequestRepository requestRepository,
                             FulfillmentOfferRepository offerRepository,
                             FulfillmentOfferLineRepository offerLineRepository,
                             SelectionRepository selectionRepository,
                             VendorProfileRepository vendorRepository,
                             ReservationRepository reservationRepository,
                             EventOutboxRepository outboxRepository,
                             EligibilityService eligibilityService,
                             InventoryClient inventoryClient,
                             OrosClient orosClient,
                             OfferFinancialsService offerFinancialsService,
                             MushexClient mushexClient,
                             FulfilmentDispatchService fulfilmentDispatchService,
                             ObjectMapper objectMapper,
                             @Value("${msika-flow.marketplace.default-fulfillment-window-hours:24}") long defaultFulfillmentWindowHours) {
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.offerLineRepository = offerLineRepository;
        this.selectionRepository = selectionRepository;
        this.vendorRepository = vendorRepository;
        this.reservationRepository = reservationRepository;
        this.outboxRepository = outboxRepository;
        this.eligibilityService = eligibilityService;
        this.inventoryClient = inventoryClient;
        this.orosClient = orosClient;
        this.offerFinancialsService = offerFinancialsService;
        this.mushexClient = mushexClient;
        this.fulfilmentDispatchService = fulfilmentDispatchService;
        this.objectMapper = objectMapper;
        this.defaultFulfillmentWindowHours = defaultFulfillmentWindowHours;
    }

    @Transactional
    public CommitResult commit(String requestId, String offerId, UUID tenantId, String actorId,
                               String idempotencyKey, HttpServletRequest inbound) {
        return commit(requestId, offerId, tenantId, actorId, idempotencyKey, null, inbound);
    }

    /**
     * OF-B14 overload — {@code splitGroupId} marks this commitment as a member
     * of a patient-chosen offer combination ({@link SplitSelectionCoordinator});
     * NULL for single-offer selections. Members run the SAME §8.9 sequence with
     * per-member compensation isolation — a member failure compensates only its
     * own holds/claim/payment, never a sibling's.
     */
    @Transactional
    public CommitResult commit(String requestId, String offerId, UUID tenantId, String actorId,
                               String idempotencyKey, String splitGroupId, HttpServletRequest inbound) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for selection (RC-1)");
        }

        // ── Step 1/2: RC-1 — idempotent replay + per-line-coverage constraint ──
        Optional<SelectionEntity> replay =
                selectionRepository.findFirstByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (replay.isPresent()) {
            SelectionEntity existing = replay.get();
            log.info("Selection replay: key={} selection={} status={}",
                    idempotencyKey, existing.getSelectionId(), existing.getStatus());
            String replayOutcome = existing.getStatus() == SelectionStatus.COMMITTED ? CODE_COMMITTED
                    : existing.getStatus() == SelectionStatus.AWAITING_PAYMENT ? CODE_AWAITING_PAYMENT
                    : existing.getFailureCode();
            return new CommitResult(existing, existing.getStatus() == SelectionStatus.COMMITTED,
                    replayOutcome, true);
        }
        assertNoActiveLineOverlap(requestId, offerId);

        MarketplaceRequestEntity request = requestRepository.findByRequestIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace request not found: " + requestId));
        if (request.getStatus() != MarketplaceRequestStatus.OFFERS_AVAILABLE
                && request.getStatus() != MarketplaceRequestStatus.SELECTION_PENDING) {
            throw new IllegalStateException("REQUEST_NOT_SELECTABLE: request status " + request.getStatus());
        }
        FulfillmentOfferEntity offer = offerRepository.findByOfferIdAndTenantId(offerId, tenantId)
                .filter(o -> requestId.equals(o.getRequestId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Offer " + offerId + " not found on request " + requestId));

        StepLog steps = new StepLog();
        SelectionEntity selection = new SelectionEntity();
        selection.setSelectionId(UlidGenerator.generate());
        selection.setTenantId(tenantId);
        selection.setRequestId(requestId);
        selection.setOfferId(offerId);
        selection.setStatus(SelectionStatus.SELECTED);
        selection.setIdempotencyKey(idempotencyKey);
        selection.setSplitGroupId(splitGroupId);
        steps.pass(1, "PATIENT_CONFIRMS_SELECTION", null, "actor=" + actorId
                + (splitGroupId != null ? " splitGroup=" + splitGroupId : ""));
        steps.pass(2, "COMMITMENT_TX_OPENED", null, "selectionId=" + selection.getSelectionId());
        selection.setStepLogJson(steps.toJson(objectMapper));
        selectionRepository.save(selection);

        if (request.getStatus() == MarketplaceRequestStatus.OFFERS_AVAILABLE) {
            MarketplaceRequestStateMachine.assertTransition(
                    request.getStatus(), MarketplaceRequestStatus.SELECTION_PENDING);
            request.setStatus(MarketplaceRequestStatus.SELECTION_PENDING);
            requestRepository.save(request);
        }

        // ── Step 3: offer revalidation (RC-2 — TTL; price snapshot is ours, lines unchanged) ──
        OffsetDateTime now = OffsetDateTime.now();
        if (offer.getStatus() != OfferStatus.ACTIVE) {
            steps.fail(3, "OFFER_REVALIDATION", CODE_OFFER_NOT_ACTIVE, "status=" + offer.getStatus());
            return failSelection(selection, request, offer, steps, CODE_OFFER_NOT_ACTIVE, false, inbound);
        }
        if (offer.getTtlExpiresAt() != null && offer.getTtlExpiresAt().isBefore(now)) {
            // RC-2: fail closed — offer expires, selection voided, honest re-offer; nothing charged.
            offer.setStatus(OfferStatus.EXPIRED);
            offer.setStatusReason(CODE_OFFER_EXPIRED);
            offerRepository.save(offer);
            steps.fail(3, "OFFER_REVALIDATION", CODE_OFFER_EXPIRED, "ttl=" + offer.getTtlExpiresAt());
            return failSelection(selection, request, offer, steps, CODE_OFFER_EXPIRED, false, inbound);
        }
        steps.pass(3, "OFFER_REVALIDATION", null,
                "ttlLive; committed price = offer snapshot " + offer.getPriceTotal() + " " + offer.getCurrency());
        OfferStateMachine.assertTransition(offer.getStatus(), OfferStatus.SELECTED);
        offer.setStatus(OfferStatus.SELECTED);
        offerRepository.save(offer);
        OfferStateMachine.assertTransition(offer.getStatus(), OfferStatus.REVALIDATING);
        offer.setStatus(OfferStatus.REVALIDATING);
        offerRepository.save(offer);
        selection.setStatus(SelectionStatus.REVALIDATING);
        selectionRepository.save(selection);

        // ── Step 4: eligibility recheck (RC-6) + §13.4 controlled hard-gate ──
        VendorProfileEntity vendor = vendorRepository.findById(offer.getVendorId())
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElse(null);
        if (vendor == null) {
            steps.fail(4, "ELIGIBILITY_RECHECK", CODE_ELIGIBILITY_LAPSED, "vendor profile missing");
            return failSelection(selection, request, offer, steps, CODE_ELIGIBILITY_LAPSED, true, inbound);
        }
        if (request.isControlled() && !eligibilityService.hasControlledAuthority(vendor)) {
            steps.fail(4, "ELIGIBILITY_RECHECK", CODE_CONTROLLED_VENDOR_UNAUTHORISED,
                    "controlled line with unauthorised vendor — hard fail (§13.4)");
            return failSelection(selection, request, offer, steps, CODE_CONTROLLED_VENDOR_UNAUTHORISED, true, inbound);
        }
        EligibilityService.EligibilityResult eligibility =
                eligibilityService.evaluate(vendor, request, inbound);
        if (!eligibility.eligible()) {
            steps.fail(4, "ELIGIBILITY_RECHECK", CODE_ELIGIBILITY_LAPSED,
                    "refusal=" + eligibility.firstRefusalCode());
            return failSelection(selection, request, offer, steps, CODE_ELIGIBILITY_LAPSED, true, inbound);
        }
        steps.pass(4, "ELIGIBILITY_RECHECK", null, null);

        // ── Step 5: DURA conditional reserve — fail-close (RC-3) ──
        // OF-B13: DIAGNOSTICS profiles have no physical stock — the step is
        // honestly recorded as a vendor service-capacity promise (no DURA hold,
        // no fabricated reservation); actual specimen/result flow stays on the
        // LIVE OROS diagnostics spine.
        List<FulfillmentOfferLineEntity> lines = offerLineRepository.findByOfferId(offerId);
        List<InventoryClient.DuraReservation> reserved = new ArrayList<>();
        if (request.getProfile() == MarketplaceProfile.DIAGNOSTICS) {
            steps.skip(5, "DURA_RESERVATION",
                    "DIAGNOSTICS profile — no physical stock to reserve; vendor service-capacity "
                            + "promise stands (window " + (offer.getFulfillmentWindowHours() != null
                                    ? offer.getFulfillmentWindowHours() + "h" : defaultFulfillmentWindowHours + "h (default)")
                            + (offer.isHomeCollection() ? "; home specimen collection "
                                    + offer.getCollectionWindowStart() + ".." + offer.getCollectionWindowEnd() : "")
                            + ")");
        } else {
            Optional<EligibilityService.DuraSource> duraSource = eligibilityService.duraSource(vendor);
            if (duraSource.isEmpty()) {
                steps.fail(5, "DURA_RESERVATION", CODE_STOCK_SOURCE_UNVERIFIED,
                        "vendor has no DURA facility/store refs — no reservation, no commitment");
                return failSelection(selection, request, offer, steps, CODE_STOCK_SOURCE_UNVERIFIED, true, inbound);
            }
            long windowHours = offer.getFulfillmentWindowHours() != null && offer.getFulfillmentWindowHours() > 0
                    ? offer.getFulfillmentWindowHours() : defaultFulfillmentWindowHours;
            OffsetDateTime reservationExpiry = now.plusHours(windowHours);
            for (FulfillmentOfferLineEntity line : lines) {
                Optional<InventoryClient.DuraReservation> hold = inventoryClient.reserve(
                        duraSource.get().facilityId(), duraSource.get().storeId(), line.getItemCode(),
                        line.getQuantity(), null, "MARKETPLACE_SELECTION", selection.getSelectionId(),
                        "marketplace commitment " + selection.getSelectionId(), reservationExpiry, inbound);
                if (hold.isEmpty()) {
                    // RC-3: conditional reserve failed — release partial holds, grade drops to REPORTED.
                    releaseReservations(reserved, inbound);
                    line.setStockGrade(StockGrade.REPORTED);
                    offerLineRepository.save(line);
                    steps.fail(5, "DURA_RESERVATION", CODE_STOCK_UNAVAILABLE,
                            "item=" + line.getItemCode() + " qty=" + line.getQuantity());
                    return failSelection(selection, request, offer, steps, CODE_STOCK_UNAVAILABLE, true, inbound);
                }
                reserved.add(hold.get());
                line.setDuraReservationRef(hold.get().reservationId().toString());
                offerLineRepository.save(line);
                writeProjection(tenantId, request, selection, line, hold.get(), reservationExpiry);
            }
            // §9.4 binding TTL semantics: from SELECTED onward, offer TTL = reservation TTL.
            OffsetDateTime boundExpiry = reserved.stream()
                    .map(InventoryClient.DuraReservation::expiresAt)
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(reservationExpiry);
            offer.setTtlExpiresAt(boundExpiry);
            offerRepository.save(offer);
            steps.pass(5, "DURA_RESERVATION", null,
                    "lines=" + reserved.size() + " reservationTtl=" + boundExpiry);
        }

        // ── Step 6: prescription claim (RC-7, medication profiles with a token) ──
        if (request.getProfile() == MarketplaceProfile.MEDICATION
                && request.getPrescriptionToken() != null && !request.getPrescriptionToken().isBlank()) {
            Optional<OrosClient.PrescriptionClaim> claim = orosClient.claimPrescription(
                    request.getPrescriptionToken(), offer.getVendorId(),
                    "marketplace-selection:" + selection.getSelectionId(), inbound);
            if (claim.isEmpty()) {
                // RC-7: version-bound claim failed — compensate the stock holds.
                releaseReservations(reserved, inbound);
                markProjectionsReleased(selection.getSelectionId());
                steps.fail(6, "PRESCRIPTION_CLAIM", CODE_CLAIM_FAILED,
                        "claim refused/unreachable — reservations released");
                return failSelection(selection, request, offer, steps, CODE_CLAIM_FAILED, true, inbound);
            }
            selection.setPrescriptionClaimId(claim.get().claimId());
            steps.pass(6, "PRESCRIPTION_CLAIM", null, "claimId=" + claim.get().claimId());
            // M3 BUG-2 — OF-B12 carriage: stamp the claim id onto the OROS
            // order's externalRefs so the oros.order.placed→pharmacy consumer
            // flow can bind claim→episode. Best-effort by design: the pharmacy
            // side ALSO lazily re-fetches external refs at completeDispense
            // (the order event may already have been consumed before this
            // commitment ran), so a failed write here never blocks commitment.
            if (request.getOrosOrderId() != null && !orosClient.recordExternalRef(
                    request.getOrosOrderId(), "prescriptionClaimId", claim.get().claimId(), inbound)) {
                log.warn("OF-B12 carriage write failed: order {} claim {} — pharmacy lazy bind remains",
                        request.getOrosOrderId(), claim.get().claimId());
            }
        } else if (request.getProfile() == MarketplaceProfile.DIAGNOSTICS) {
            // OF-B13: diagnostics fulfil the OROS order directly — no
            // prescription, no claim; results route on the LIVE OROS spine.
            steps.skip(6, "PRESCRIPTION_CLAIM",
                    "DIAGNOSTICS profile — no prescription to claim; OROS order "
                            + request.getOrosOrderId() + " remains the diagnostics spine truth");
        } else {
            steps.skip(6, "PRESCRIPTION_CLAIM", "no prescription token / non-medication profile");
        }

        // ── Step 7: financial clearance re-verification (OF-B9/OF-B8, §8.7/§10.4) ──
        OfferFinancialsService.FinancialBlock financials =
                offerFinancialsService.compute(request, offer, lines, inbound);
        String financialJson = offerFinancialsService.toJson(financials);
        selection.setFinancialJson(financialJson);
        selection.setPaStatus(financials.paStatus());
        selection.setPaReference(financials.paReference() != null
                ? financials.paReference().toString() : null);
        // Display truth: the step-7 block is stamped onto the offer's snapshot
        // whatever the outcome (VERIFIED / NOT_COVERED / UNVERIFIED / SELF_PAY).
        attachFinancialsToOfferSnapshot(offer, financialJson);
        if (financials.fundingMode() == FundingMode.PAYER_COVERED) {
            if (!financials.payerVerified()) {
                // §10.4/§10.5: unverifiable or refused coverage FAILS CLOSED for
                // payer-covered flows — the stock holds and claim are compensated.
                String code = OfferFinancialsService.STATUS_NOT_COVERED.equals(financials.status())
                        ? CODE_NOT_COVERED : CODE_FINANCIAL_UNVERIFIED;
                releaseReservations(reserved, inbound);
                markProjectionsReleased(selection.getSelectionId());
                releaseClaimIfAny(selection, inbound);
                steps.fail(7, "FINANCIAL_CLEARANCE", code,
                        "status=" + financials.status()
                                + (financials.detail() != null ? " — " + financials.detail() : ""));
                return failSelection(selection, request, offer, steps, code, true, inbound);
            }
            if (!financials.paSatisfied()) {
                // OF-B8 §8.7.6: this offer is conditional on approval — PA
                // not-approved refuses the commitment with the coded outcome.
                releaseReservations(reserved, inbound);
                markProjectionsReleased(selection.getSelectionId());
                releaseClaimIfAny(selection, inbound);
                steps.fail(7, "FINANCIAL_CLEARANCE", CODE_PA_REQUIRED,
                        "paStatus=" + financials.paStatus()
                                + (financials.paReference() != null
                                        ? " authorisation=" + financials.paReference() : ""));
                return failSelection(selection, request, offer, steps, CODE_PA_REQUIRED, true, inbound);
            }
            steps.pass(7, "FINANCIAL_CLEARANCE", null,
                    "VERIFIED covered=" + financials.coveredAmount()
                            + " patient=" + financials.patientLiability() + " " + financials.currency()
                            + " (ESTIMATE — never final, §10.5)"
                            + (financials.paRequired() ? " paStatus=" + financials.paStatus() : ""));
        } else {
            steps.pass(7, "FINANCIAL_CLEARANCE", null,
                    "explicit self-pay election — due-now = offer price "
                            + financials.patientLiability() + " " + financials.currency());
        }

        // ── Step 8: payment execution (OF-B10, §8.8 — intent → PAID, no 2-phase capture) ──
        BigDecimal shortfall = financials.shortfall();
        if (shortfall == null || shortfall.signum() <= 0) {
            // Zero due-now shortfall (fully covered) — recorded WAIVED, never silently skipped.
            selection.setShortfallAmount(BigDecimal.ZERO);
            selection.setShortfallCurrency(financials.currency());
            steps.waived(8, "PAYMENT_EXECUTION", "zero due-now shortfall — nothing to charge");
        } else {
            Optional<MushexClient.MushexIntentCreated> intent = mushexClient.createSelectionPaymentIntent(
                    inbound, selection.getSelectionId(), shortfall, financials.currency());
            if (intent.isEmpty()) {
                // No real intent → no AWAITING_PAYMENT limbo: fail closed with compensation.
                releaseReservations(reserved, inbound);
                markProjectionsReleased(selection.getSelectionId());
                releaseClaimIfAny(selection, inbound);
                steps.fail(8, "PAYMENT_EXECUTION", CODE_PAYMENT_INTENT_FAILED,
                        "MusheX intent create failed — holds and claim compensated");
                return failSelection(selection, request, offer, steps, CODE_PAYMENT_INTENT_FAILED, true, inbound);
            }
            selection.setPaymentIntentId(intent.get().intentId());
            selection.setPaymentStatus(intent.get().status());
            selection.setShortfallAmount(shortfall);
            selection.setShortfallCurrency(financials.currency());
            selection.setStatus(SelectionStatus.AWAITING_PAYMENT);
            steps.pending(8, "PAYMENT_EXECUTION",
                    "intent " + intent.get().intentId() + " opened for " + shortfall + " "
                            + financials.currency() + " — held until PAID (reservation TTL is the retry window)");
            selection.setStepLogJson(steps.toJson(objectMapper));
            selectionRepository.save(selection);
            publishSelectionEvent(selection, request, offer, "SELECTION_AWAITING_PAYMENT", CODE_AWAITING_PAYMENT);
            log.info("Selection awaiting payment: selection={} intent={} amount={} {}",
                    selection.getSelectionId(), intent.get().intentId(), shortfall, financials.currency());
            return new CommitResult(selection, false, CODE_AWAITING_PAYMENT, false);
        }

        // ── Steps 9–12 (waived-payment path completes synchronously) ──
        return completeCommitment(selection, request, offer, lines, vendor, steps, inbound);
    }

    /**
     * OF-B10 — payment-event resume seam ({@code mushex.payment.status.changed}
     * consumed by {@code PaymentEventConsumer}). PAID resumes steps 9–12;
     * terminal failure compensates per RC-5 (holds + claim released); interim
     * statuses only refresh the projection. CC-2 holds: a payment event alone
     * never sets fulfilment state — it resumes the guarded commitment sequence,
     * which still enforces §13.4 and the state machines.
     *
     * @return the outcome when a held selection was resumed; empty when the
     *         intent maps to no held selection (not a marketplace payment).
     */
    @Transactional
    public Optional<CommitResult> onPaymentStatusChanged(String mushexPaymentIntentId, String status) {
        Optional<SelectionEntity> found = selectionRepository.findFirstByPaymentIntentId(mushexPaymentIntentId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SelectionEntity selection = found.get();
        String normalised = status != null ? status.trim().toUpperCase() : "";
        if (selection.getStatus() != SelectionStatus.AWAITING_PAYMENT) {
            // Late/duplicate event — refresh the projection only, never re-run steps.
            selection.setPaymentStatus(normalised);
            selectionRepository.save(selection);
            log.info("Payment event for non-held selection {} (status={}) — projection updated only",
                    selection.getSelectionId(), selection.getStatus());
            return Optional.empty();
        }
        selection.setPaymentStatus(normalised);
        MarketplaceRequestEntity request = requestRepository.findById(selection.getRequestId()).orElse(null);
        FulfillmentOfferEntity offer = offerRepository.findById(selection.getOfferId()).orElse(null);
        if (request == null || offer == null) {
            log.error("Held selection {} lost its request/offer rows — cannot resume", selection.getSelectionId());
            selectionRepository.save(selection);
            return Optional.empty();
        }
        List<FulfillmentOfferLineEntity> lines = offerLineRepository.findByOfferId(selection.getOfferId());
        StepLog steps = StepLog.fromJson(objectMapper, selection.getStepLogJson());
        switch (normalised) {
            case "PAID" -> {
                VendorProfileEntity vendor = vendorRepository.findById(offer.getVendorId())
                        .filter(v -> selection.getTenantId().equals(v.getTenantId()))
                        .orElse(null);
                steps.pass(8, "PAYMENT_EXECUTION", null,
                        "intent " + mushexPaymentIntentId + " PAID — commitment resumed");
                return Optional.of(completeCommitment(selection, request, offer, lines, vendor, steps, null));
            }
            case "FAILED", "CANCELLED", "REJECTED" -> {
                // §8.8.4 + lane doctrine: terminal failure compensates — DURA holds
                // and the prescription claim are released; nothing was charged.
                releaseLineReservations(lines, null);
                markProjectionsReleased(selection.getSelectionId());
                releaseClaimIfAny(selection, null);
                steps.fail(8, "PAYMENT_EXECUTION", CODE_PAYMENT_FAILED,
                        "intent " + mushexPaymentIntentId + " " + normalised + " — holds and claim compensated");
                return Optional.of(failSelection(selection, request, offer, steps, CODE_PAYMENT_FAILED, true, null));
            }
            default -> {
                // PENDING / AUTHORIZED / PARTIALLY_PAID …: projection refresh only.
                selectionRepository.save(selection);
                return Optional.empty();
            }
        }
    }

    /**
     * OF-B10 timeout backstop (§8.8.4): an AWAITING_PAYMENT selection whose
     * offer TTL (re-bound to the DURA reservation TTL at step 5) has lapsed is
     * compensated and failed with {@code PAYMENT_TIMEOUT} — no zombie holds,
     * honest re-offer posture. Invoked by {@link MarketplaceSweeper}.
     */
    @Transactional
    public int sweepAwaitingPayment(OffsetDateTime now) {
        int swept = 0;
        for (SelectionEntity selection : selectionRepository.findByStatus(SelectionStatus.AWAITING_PAYMENT)) {
            FulfillmentOfferEntity offer = offerRepository.findById(selection.getOfferId()).orElse(null);
            MarketplaceRequestEntity request = requestRepository.findById(selection.getRequestId()).orElse(null);
            if (offer == null || request == null) {
                continue;
            }
            if (offer.getTtlExpiresAt() == null || offer.getTtlExpiresAt().isAfter(now)) {
                continue; // retry window (reservation TTL) still open
            }
            List<FulfillmentOfferLineEntity> lines = offerLineRepository.findByOfferId(selection.getOfferId());
            releaseLineReservations(lines, null);
            markProjectionsReleased(selection.getSelectionId());
            releaseClaimIfAny(selection, null);
            StepLog steps = StepLog.fromJson(objectMapper, selection.getStepLogJson());
            steps.fail(8, "PAYMENT_EXECUTION", CODE_PAYMENT_TIMEOUT,
                    "payment window (reservation TTL) lapsed at " + offer.getTtlExpiresAt()
                            + " — holds and claim compensated");
            failSelection(selection, request, offer, steps, CODE_PAYMENT_TIMEOUT, true, null);
            swept++;
        }
        return swept;
    }

    /**
     * Steps 9–12 of the §8.9 sequence — shared by the synchronous (waived
     * payment) path and the PAID-callback resume path. Compensation here is the
     * full RC-5 chain: DURA holds + claim released AND a PAID intent refunded.
     */
    private CommitResult completeCommitment(SelectionEntity selection, MarketplaceRequestEntity request,
                                            FulfillmentOfferEntity offer, List<FulfillmentOfferLineEntity> lines,
                                            VendorProfileEntity vendor, StepLog steps,
                                            HttpServletRequest inbound) {
        // ── §13.4: mandatory controlled-register write before commitment completes ──
        if (request.isControlled()) {
            Optional<EligibilityService.DuraSource> duraSource =
                    vendor != null ? eligibilityService.duraSource(vendor) : Optional.empty();
            boolean allWritten = duraSource.isPresent();
            if (allWritten) {
                for (FulfillmentOfferLineEntity line : lines) {
                    boolean written = inventoryClient.recordControlledIssue(
                            duraSource.get().facilityId(), duraSource.get().storeId(), line.getItemCode(),
                            BigDecimal.valueOf(line.getQuantity()), null,
                            eligibilityService.providerRef(vendor), selection.getSelectionId(), inbound);
                    if (!written) {
                        allWritten = false;
                        break;
                    }
                }
            }
            if (!allWritten) {
                releaseLineReservations(lines, inbound);
                markProjectionsReleased(selection.getSelectionId());
                releaseClaimIfAny(selection, inbound);
                refundIfPaid(selection, inbound);
                steps.fail(9, "CONTROLLED_REGISTER_WRITE", CODE_CONTROLLED_REGISTER_WRITE_FAILED,
                        "controlled commitment cannot complete without the register write (§13.4)");
                return failSelection(selection, request, offer, steps,
                        CODE_CONTROLLED_REGISTER_WRITE_FAILED, true, inbound);
            }
            steps.pass(9, "CONTROLLED_REGISTER_WRITE", null,
                    "refType=MARKETPLACE_COMMITMENT refId=" + selection.getSelectionId());
        }

        // ── Step 9: commitment record written ──
        selection.setStatus(SelectionStatus.COMMITTED);
        selection.setCommittedAt(OffsetDateTime.now());
        // OF-B13 seam: the OROS order ref rides the commitment so downstream
        // specimen/result flow routes on the LIVE OROS spine — no new result
        // machinery in the marketplace.
        selection.setOrosOrderId(request.getOrosOrderId());
        String expectationDetail = "orosOrderId=" + request.getOrosOrderId();
        if (request.getProfile() == MarketplaceProfile.DIAGNOSTICS) {
            String collectionMode = offer.isHomeCollection() ? "HOME_COLLECTION" : "WALK_IN";
            selection.setCollectionMode(collectionMode);
            selection.setCollectionWindowStart(offer.getCollectionWindowStart());
            selection.setCollectionWindowEnd(offer.getCollectionWindowEnd());
            expectationDetail += " collectionMode=" + collectionMode
                    + (offer.getCollectionWindowStart() != null
                            ? " collectionWindow=" + offer.getCollectionWindowStart()
                                    + ".." + offer.getCollectionWindowEnd() : "");
        }
        steps.pass(10, "COMMITMENT_RECORDED", null, expectationDetail);
        OfferStateMachine.assertTransition(offer.getStatus(), OfferStatus.COMMITTED);
        offer.setStatus(OfferStatus.COMMITTED);
        offerRepository.save(offer);

        // OF-B14 — the request rolls to COMMITTED only when every published
        // line is covered by a COMMITTED selection. A split member covering a
        // subset returns the request to the honest OFFERS_AVAILABLE posture so
        // the remaining lines stay live (§8.6.4 — members already committed
        // STAND regardless of what happens to their siblings).
        Set<String> requestRefs = requestLineRefs(request);
        Set<String> committedRefs = committedCoverage(request, selection, lines);
        boolean fullyCovered = requestRefs.isEmpty() || committedRefs.containsAll(requestRefs);
        if (fullyCovered) {
            MarketplaceRequestStateMachine.assertTransition(
                    request.getStatus(), MarketplaceRequestStatus.COMMITTED);
            request.setStatus(MarketplaceRequestStatus.COMMITTED);
            requestRepository.save(request);
        } else {
            if (request.getStatus() == MarketplaceRequestStatus.SELECTION_PENDING) {
                MarketplaceRequestStateMachine.assertTransition(
                        request.getStatus(), MarketplaceRequestStatus.OFFERS_AVAILABLE);
                request.setStatus(MarketplaceRequestStatus.OFFERS_AVAILABLE);
                requestRepository.save(request);
            }
            publishPartialCommitmentEvent(request, selection, requestRefs, committedRefs);
        }

        // ── Step 10: losing offers released — ONLY offers overlapping the
        // committed coverage; disjoint offers stay ACTIVE for split composition
        // (OF-B14). Once the request is fully covered, every remaining ACTIVE
        // offer is released (the round is closed).
        Set<String> committedOfferRefs = new HashSet<>();
        for (FulfillmentOfferLineEntity line : lines) {
            if (line.getRequestLineRef() != null) {
                committedOfferRefs.add(line.getRequestLineRef());
            }
        }
        int releasedCount = 0;
        int keptActive = 0;
        for (FulfillmentOfferEntity other : offerRepository.findByRequestId(request.getRequestId())) {
            if (other.getOfferId().equals(offer.getOfferId()) || other.getStatus() != OfferStatus.ACTIVE) {
                continue;
            }
            Set<String> otherRefs = lineRefsOfOffer(other.getOfferId());
            boolean overlaps = otherRefs.isEmpty()
                    || otherRefs.stream().anyMatch(committedOfferRefs::contains);
            if (fullyCovered || overlaps) {
                other.setStatus(OfferStatus.NOT_SELECTED);
                other.setStatusReason("ANOTHER_OFFER_COMMITTED");
                offerRepository.save(other);
                releasedCount++;
            } else {
                keptActive++;
            }
        }
        steps.pass(11, "LOSING_OFFERS_RELEASED", null,
                "released=" + releasedCount
                        + (keptActive > 0 ? " keptActiveDisjoint=" + keptActive + " (split posture)" : ""));

        // ── Step 11: fulfilment dispatch (OF-B17, RC-8 — idempotent on selectionId) ──
        // Dispatch failure NEVER rolls back the commitment: the order stands with a
        // coded DISPATCH_FAILED outcome and the retry sweep re-attempts (§8.9 step 11).
        FulfilmentDispatchService.DispatchOutcome dispatch =
                fulfilmentDispatchService.dispatchOnCommit(selection, request, offer, lines, inbound);
        if (dispatch.dispatched()) {
            steps.pass(12, "FULFILMENT_DISPATCH", null, dispatch.detail());
        } else if (dispatch.failed()) {
            steps.partial(12, "FULFILMENT_DISPATCH", dispatch.code(),
                    dispatch.detail() + " — commitment stands; retry sweep re-attempts (RC-8)");
        } else {
            steps.skip(12, "FULFILMENT_DISPATCH", dispatch.detail());
        }

        selection.setStepLogJson(steps.toJson(objectMapper));
        selectionRepository.save(selection);

        // ── Step 12: events ──
        publishSelectionEvent(selection, request, offer, "SELECTION_COMMITTED", CODE_COMMITTED);
        log.info("Selection committed: selection={} request={} offer={} vendor={}",
                selection.getSelectionId(), request.getRequestId(), offer.getOfferId(), offer.getVendorId());
        return new CommitResult(selection, true, CODE_COMMITTED, false);
    }

    // ── OF-B14 per-line-coverage plumbing ────────────────────────────────

    /**
     * RC-1 extended (OF-B14): the single-active-selection invariant is
     * per-LINE-coverage — no two live (non-FAILED/CANCELLED) selections may
     * cover the same published request line. A full-coverage offer therefore
     * still conflicts with any live selection (the Wave OF-A behaviour) while
     * disjoint split members pass. This invariant spans two tables and is not
     * expressible as a partial index, so code + the
     * {@code uq_mf_selection_offer_active} index (same offer twice) carry it.
     * Unresolvable coverage blocks conservatively — fail-closed, never a
     * silent double-sell.
     */
    private void assertNoActiveLineOverlap(String requestId, String offerId) {
        List<SelectionEntity> live = selectionRepository.findByRequestId(requestId).stream()
                .filter(s -> s.getStatus() != SelectionStatus.FAILED
                        && s.getStatus() != SelectionStatus.CANCELLED)
                .toList();
        if (live.isEmpty()) {
            return;
        }
        Set<String> candidateRefs = lineRefsOfOffer(offerId);
        for (SelectionEntity s : live) {
            if (s.getOfferId() == null || s.getOfferId().equals(offerId)) {
                throw new IllegalStateException("SELECTION_EXISTS: request " + requestId
                        + " already has an active selection for this offer coverage (RC-1)");
            }
            Set<String> otherRefs = lineRefsOfOffer(s.getOfferId());
            if (candidateRefs.isEmpty() || otherRefs.isEmpty()
                    || otherRefs.stream().anyMatch(candidateRefs::contains)) {
                throw new IllegalStateException("SELECTION_EXISTS: request " + requestId
                        + " has an active selection covering the same line(s) (RC-1 per-line, OF-B14)");
            }
        }
    }

    private Set<String> lineRefsOfOffer(String offerId) {
        Set<String> refs = new HashSet<>();
        for (FulfillmentOfferLineEntity line : offerLineRepository.findByOfferId(offerId)) {
            if (line.getRequestLineRef() != null) {
                refs.add(line.getRequestLineRef());
            }
        }
        return refs;
    }

    /** Published-snapshot line refs — the request-side coverage truth. */
    private Set<String> requestLineRefs(MarketplaceRequestEntity request) {
        Set<String> refs = new HashSet<>();
        try {
            com.fasterxml.jackson.databind.JsonNode snapshot =
                    objectMapper.readTree(request.getPublishedLinesJson());
            for (com.fasterxml.jackson.databind.JsonNode line : snapshot.path("lines")) {
                String ref = line.path("lineRef").asText(null);
                if (ref != null && !ref.isBlank()) {
                    refs.add(ref);
                }
            }
        } catch (Exception e) {
            log.warn("Unparseable snapshot for request {}: {}", request.getRequestId(), e.getMessage());
        }
        return refs;
    }

    /**
     * Union of line refs covered by COMMITTED selections on this request —
     * the just-committed selection's lines plus every previously committed
     * sibling's (split members commit serially).
     */
    private Set<String> committedCoverage(MarketplaceRequestEntity request, SelectionEntity current,
                                          List<FulfillmentOfferLineEntity> currentLines) {
        Set<String> covered = new HashSet<>();
        for (FulfillmentOfferLineEntity line : currentLines) {
            if (line.getRequestLineRef() != null) {
                covered.add(line.getRequestLineRef());
            }
        }
        for (SelectionEntity s : selectionRepository.findByRequestId(request.getRequestId())) {
            if (s.getStatus() == SelectionStatus.COMMITTED
                    && !Objects.equals(s.getSelectionId(), current.getSelectionId())
                    && s.getOfferId() != null) {
                covered.addAll(lineRefsOfOffer(s.getOfferId()));
            }
        }
        return covered;
    }

    /**
     * OF-B14 — honest partial posture event: a member committed but the
     * request's lines are not yet fully covered. PII-free: refs and ids only.
     */
    private void publishPartialCommitmentEvent(MarketplaceRequestEntity request, SelectionEntity selection,
                                               Set<String> requestRefs, Set<String> committedRefs) {
        try {
            Set<String> uncovered = new HashSet<>(requestRefs);
            uncovered.removeAll(committedRefs);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getRequestId());
            payload.put("tenantId", request.getTenantId().toString());
            payload.put("status", request.getStatus().name());
            payload.put("committedSelectionId", selection.getSelectionId());
            if (selection.getSplitGroupId() != null) {
                payload.put("splitGroupId", selection.getSplitGroupId());
            }
            payload.put("coveredLineRefs", committedRefs.stream().sorted().toList());
            payload.put("uncoveredLineRefs", uncovered.stream().sorted().toList());
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("MarketplaceRequest");
            outbox.setAggregateId(request.getRequestId());
            outbox.setEventType("REQUEST_PARTIALLY_COMMITTED");
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(request.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write REQUEST_PARTIALLY_COMMITTED event for request {}: {}",
                    request.getRequestId(), e.getMessage());
        }
    }

    // ── failure + compensation plumbing ──────────────────────────────────

    private CommitResult failSelection(SelectionEntity selection, MarketplaceRequestEntity request,
                                       FulfillmentOfferEntity offer, StepLog steps, String code,
                                       boolean failOfferRevalidation, HttpServletRequest inbound) {
        if (failOfferRevalidation && !OfferStateMachine.isTerminal(offer.getStatus())) {
            // RC-3/6/7 family: the offer is honestly failed with the coded cause.
            if (OfferStateMachine.canTransition(offer.getStatus(), OfferStatus.FAILED_REVALIDATION)) {
                offer.setStatus(OfferStatus.FAILED_REVALIDATION);
                offer.setStatusReason(code);
                offerRepository.save(offer);
            }
        }
        selection.setStatus(SelectionStatus.FAILED);
        selection.setFailureCode(code);
        selection.setStepLogJson(steps.toJson(objectMapper));
        selectionRepository.save(selection);

        // The request returns to the honest re-offer posture.
        if (request.getStatus() == MarketplaceRequestStatus.SELECTION_PENDING) {
            MarketplaceRequestStateMachine.assertTransition(
                    request.getStatus(), MarketplaceRequestStatus.OFFERS_AVAILABLE);
            request.setStatus(MarketplaceRequestStatus.OFFERS_AVAILABLE);
            requestRepository.save(request);
        }
        publishSelectionEvent(selection, request, offer, "SELECTION_FAILED", code);
        log.warn("Selection failed: selection={} request={} code={}",
                selection.getSelectionId(), request.getRequestId(), code);
        return new CommitResult(selection, false, code, false);
    }

    private void releaseReservations(List<InventoryClient.DuraReservation> reserved,
                                     HttpServletRequest inbound) {
        for (InventoryClient.DuraReservation hold : reserved) {
            boolean released = inventoryClient.release(hold.reservationId(), inbound);
            if (!released) {
                log.error("Compensation release failed for DURA reservation {} — TTL sweep is the backstop",
                        hold.reservationId());
            }
        }
    }

    /**
     * Line-ref-based compensation twin of {@link #releaseReservations} for the
     * deferred paths (payment callback / timeout sweep) where the in-memory
     * hold list no longer exists — the refs were persisted on the offer lines
     * at step 5.
     */
    private void releaseLineReservations(List<FulfillmentOfferLineEntity> lines, HttpServletRequest inbound) {
        for (FulfillmentOfferLineEntity line : lines) {
            if (line.getDuraReservationRef() == null) {
                continue;
            }
            try {
                boolean released = inventoryClient.release(UUID.fromString(line.getDuraReservationRef()), inbound);
                if (!released) {
                    log.error("Compensation release failed for DURA reservation {} — TTL sweep is the backstop",
                            line.getDuraReservationRef());
                }
            } catch (IllegalArgumentException e) {
                log.error("Unparseable DURA reservation ref {} on line {}",
                        line.getDuraReservationRef(), line.getOfferLineId());
            }
        }
    }

    /**
     * OF-B9 — merges the step-7 financial block into the offer's eligibility
     * snapshot (§8.7 "offer view + eligibility snapshot" contract) so the
     * audited snapshot carries the money truth the patient acted on.
     */
    private void attachFinancialsToOfferSnapshot(FulfillmentOfferEntity offer, String financialJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode existing =
                    offer.getEligibilitySnapshotJson() != null && !offer.getEligibilitySnapshotJson().isBlank()
                            ? objectMapper.readTree(offer.getEligibilitySnapshotJson())
                            : objectMapper.createObjectNode();
            if (existing instanceof ObjectNode snapshot) {
                snapshot.set("financials", objectMapper.readTree(financialJson));
                offer.setEligibilitySnapshotJson(snapshot.toString());
                offerRepository.save(offer);
            }
        } catch (Exception e) {
            log.warn("Could not attach financial block to offer {} snapshot: {}",
                    offer.getOfferId(), e.getMessage());
        }
    }

    /**
     * RC-5: a PAID intent whose commitment later failed is refunded
     * automatically and visibly (§8.8.4) — never a silently kept payment.
     */
    private void refundIfPaid(SelectionEntity selection, HttpServletRequest inbound) {
        if (selection.getPaymentIntentId() != null && "PAID".equalsIgnoreCase(selection.getPaymentStatus())
                && selection.getShortfallAmount() != null && selection.getShortfallAmount().signum() > 0) {
            mushexClient.tryRefund(inbound, selection.getPaymentIntentId(), selection.getShortfallAmount(),
                    "COMMITMENT_COMPENSATION:" + selection.getSelectionId());
        }
    }

    private void releaseClaimIfAny(SelectionEntity selection, HttpServletRequest inbound) {
        if (selection.getPrescriptionClaimId() != null) {
            orosClient.releaseClaim(selection.getPrescriptionClaimId(),
                    "COMMITMENT_COMPENSATION:" + selection.getSelectionId(), inbound);
        }
    }

    /** mf_reservations is a demoted read-projection of the DURA row — never a second truth. */
    private void writeProjection(UUID tenantId, MarketplaceRequestEntity request, SelectionEntity selection,
                                 FulfillmentOfferLineEntity line, InventoryClient.DuraReservation hold,
                                 OffsetDateTime fallbackExpiry) {
        ReservationEntity projection = new ReservationEntity();
        projection.setId(UlidGenerator.generate());
        projection.setOrderId(request.getOrosOrderId());
        projection.setLineId(line.getOfferLineId());
        projection.setSystem("INVENTORY");
        projection.setStatus(ReservationStatus.CONFIRMED);
        projection.setReservationRef(hold.reservationId().toString());
        projection.setExpiresAt(hold.expiresAt() != null ? hold.expiresAt() : fallbackExpiry);
        reservationRepository.save(projection);
    }

    private void markProjectionsReleased(String selectionId) {
        // Projection rows were written per reserved line before the failing step;
        // mirror the compensation release so the projection never disagrees with DURA.
        offerLineRepositoryLinesFor(selectionId).forEach(ref ->
                reservationRepository.findFirstByReservationRef(ref).ifPresent(row -> {
                    row.setStatus(ReservationStatus.RELEASED);
                    reservationRepository.save(row);
                }));
    }

    private List<String> offerLineRepositoryLinesFor(String selectionId) {
        return selectionRepository.findById(selectionId)
                .map(sel -> offerLineRepository.findByOfferId(sel.getOfferId()).stream()
                        .map(FulfillmentOfferLineEntity::getDuraReservationRef)
                        .filter(java.util.Objects::nonNull)
                        .toList())
                .orElse(List.of());
    }

    private void publishSelectionEvent(SelectionEntity selection, MarketplaceRequestEntity request,
                                       FulfillmentOfferEntity offer, String eventType, String code) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("selectionId", selection.getSelectionId());
            payload.put("requestId", request.getRequestId());
            payload.put("offerId", offer.getOfferId());
            payload.put("vendorId", offer.getVendorId());
            payload.put("tenantId", selection.getTenantId().toString());
            payload.put("status", selection.getStatus().name());
            payload.put("outcomeCode", code);
            payload.put("profile", request.getProfile() != null ? request.getProfile().name() : null);
            // OF-B14 — split members carry their group id on every event.
            if (selection.getSplitGroupId() != null) {
                payload.put("splitGroupId", selection.getSplitGroupId());
            }
            // OF-B13 — the OROS spine seam + collection expectation.
            if (selection.getOrosOrderId() != null) {
                payload.put("orosOrderId", selection.getOrosOrderId());
            }
            if (selection.getCollectionMode() != null) {
                payload.put("collectionMode", selection.getCollectionMode());
                if (selection.getCollectionWindowStart() != null) {
                    payload.put("collectionWindowStart", selection.getCollectionWindowStart().toString());
                    payload.put("collectionWindowEnd", selection.getCollectionWindowEnd() != null
                            ? selection.getCollectionWindowEnd().toString() : null);
                }
            }
            payload.put("priceTotal", offer.getPriceTotal() != null ? offer.getPriceTotal().toPlainString() : null);
            payload.put("currency", offer.getCurrency());
            // OF-B9/OF-B10 — the financial posture rides every selection event
            // (msika.flow.selection financial status; §8.7/§8.8 event contract).
            if (selection.getFinancialJson() != null) {
                try {
                    payload.put("financials", objectMapper.readTree(selection.getFinancialJson()));
                } catch (Exception ignored) {
                    // unparseable snapshot — omit rather than fail the event
                }
            }
            if (selection.getPaymentIntentId() != null) {
                payload.put("paymentIntentId", selection.getPaymentIntentId());
                payload.put("paymentStatus", selection.getPaymentStatus());
                payload.put("shortfallAmount", selection.getShortfallAmount() != null
                        ? selection.getShortfallAmount().toPlainString() : null);
                payload.put("shortfallCurrency", selection.getShortfallCurrency());
            }
            if (selection.getPaStatus() != null) {
                payload.put("paStatus", selection.getPaStatus());
                payload.put("paReference", selection.getPaReference());
            }
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("MarketplaceSelection");
            outbox.setAggregateId(selection.getSelectionId());
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(selection.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write selection outbox event {}: {}", eventType, e.getMessage());
        }
    }

    // ── §8.9 step log ────────────────────────────────────────────────────

    static final class StepLog {
        private record Entry(int seq, String step, String status, String code, String detail, OffsetDateTime at) {}
        private final List<Entry> entries = new ArrayList<>();

        void pass(int seq, String step, String code, String detail) {
            entries.add(new Entry(seq, step, "PASS", code, detail, OffsetDateTime.now()));
        }

        void fail(int seq, String step, String code, String detail) {
            entries.add(new Entry(seq, step, "FAIL", code, detail, OffsetDateTime.now()));
        }

        void skip(int seq, String step, String detail) {
            entries.add(new Entry(seq, step, "SKIPPED", null, detail, OffsetDateTime.now()));
        }

        void partial(int seq, String step, String detail) {
            entries.add(new Entry(seq, step, "PARTIAL", null, detail, OffsetDateTime.now()));
        }

        /** OF-B17 — PARTIAL with a discrete code (e.g. DISPATCH_FAILED, honest not-fatal). */
        void partial(int seq, String step, String code, String detail) {
            entries.add(new Entry(seq, step, "PARTIAL", code, detail, OffsetDateTime.now()));
        }

        /** OF-B10 — zero-shortfall payment step: nothing owed, recorded, never skipped. */
        void waived(int seq, String step, String detail) {
            entries.add(new Entry(seq, step, "WAIVED", null, detail, OffsetDateTime.now()));
        }

        /** OF-B10 — step opened but held (AWAITING_PAYMENT); a later entry resolves it. */
        void pending(int seq, String step, String detail) {
            entries.add(new Entry(seq, step, "PENDING", null, detail, OffsetDateTime.now()));
        }

        /**
         * Rehydrates the append-only log for the deferred resume paths so the
         * PAID/FAILED continuation appends to the original history instead of
         * replacing it.
         */
        static StepLog fromJson(ObjectMapper mapper, String json) {
            StepLog log = new StepLog();
            if (json == null || json.isBlank()) {
                return log;
            }
            try {
                com.fasterxml.jackson.databind.JsonNode arr = mapper.readTree(json);
                if (arr.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                        OffsetDateTime at;
                        try {
                            at = OffsetDateTime.parse(n.path("at").asText());
                        } catch (Exception e) {
                            at = OffsetDateTime.now();
                        }
                        log.entries.add(new Entry(
                                n.path("seq").asInt(),
                                n.path("step").asText(null),
                                n.path("status").asText(null),
                                n.hasNonNull("code") ? n.get("code").asText() : null,
                                n.hasNonNull("detail") ? n.get("detail").asText() : null,
                                at));
                    }
                }
            } catch (Exception e) {
                // Unparseable history: keep going with a fresh log rather than losing the outcome.
            }
            return log;
        }

        String toJson(ObjectMapper mapper) {
            ArrayNode arr = mapper.createArrayNode();
            for (Entry e : entries) {
                ObjectNode n = arr.addObject();
                n.put("seq", e.seq());
                n.put("step", e.step());
                n.put("status", e.status());
                if (e.code() != null) n.put("code", e.code());
                if (e.detail() != null) n.put("detail", e.detail());
                n.put("at", e.at().toString());
            }
            return arr.toString();
        }
    }
}
