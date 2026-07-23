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
import zw.gov.mohcc.impilo.msikaflow.integration.OrosClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OF-B6/OF-B11 — the §8.9 commitment orchestrator for the wave's scope:
 * revalidate offer (RC-2/RC-4) → eligibility recheck (RC-6) → DURA conditional
 * reserve, fail-close (RC-3) → OROS prescription claim (RC-7) → controlled-register
 * write (§13.4) → COMMITTED → losing offers released → dispatch seam → events.
 *
 * <p>RC-1 rides the Idempotency-Key + single-active-selection invariant;
 * RC-5-style compensation releases the DURA holds and the OROS claim on any
 * later-step failure; RC-8 dispatch is idempotent on the selection id.
 * Financial clearance and payment (steps 7–8) are OF-B9/OF-B10 scope and are
 * recorded as SKIPPED in the step log — honestly, not silently.</p>
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
        this.objectMapper = objectMapper;
        this.defaultFulfillmentWindowHours = defaultFulfillmentWindowHours;
    }

    @Transactional
    public CommitResult commit(String requestId, String offerId, UUID tenantId, String actorId,
                               String idempotencyKey, HttpServletRequest inbound) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for selection (RC-1)");
        }

        // ── Step 1/2: RC-1 — idempotent replay + single-active-selection ──
        Optional<SelectionEntity> replay =
                selectionRepository.findFirstByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (replay.isPresent()) {
            SelectionEntity existing = replay.get();
            log.info("Selection replay: key={} selection={} status={}",
                    idempotencyKey, existing.getSelectionId(), existing.getStatus());
            return new CommitResult(existing, existing.getStatus() == SelectionStatus.COMMITTED,
                    existing.getStatus() == SelectionStatus.COMMITTED ? CODE_COMMITTED
                            : existing.getFailureCode(), true);
        }
        boolean liveSelectionExists = selectionRepository.findByRequestId(requestId).stream()
                .anyMatch(s -> s.getStatus() != SelectionStatus.FAILED
                        && s.getStatus() != SelectionStatus.CANCELLED);
        if (liveSelectionExists) {
            throw new IllegalStateException(
                    "SELECTION_EXISTS: request " + requestId + " already has an active selection (RC-1)");
        }

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
        steps.pass(1, "PATIENT_CONFIRMS_SELECTION", null, "actor=" + actorId);
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
        List<FulfillmentOfferLineEntity> lines = offerLineRepository.findByOfferId(offerId);
        Optional<EligibilityService.DuraSource> duraSource = eligibilityService.duraSource(vendor);
        if (duraSource.isEmpty()) {
            steps.fail(5, "DURA_RESERVATION", CODE_STOCK_SOURCE_UNVERIFIED,
                    "vendor has no DURA facility/store refs — no reservation, no commitment");
            return failSelection(selection, request, offer, steps, CODE_STOCK_SOURCE_UNVERIFIED, true, inbound);
        }
        long windowHours = offer.getFulfillmentWindowHours() != null && offer.getFulfillmentWindowHours() > 0
                ? offer.getFulfillmentWindowHours() : defaultFulfillmentWindowHours;
        OffsetDateTime reservationExpiry = now.plusHours(windowHours);
        List<InventoryClient.DuraReservation> reserved = new ArrayList<>();
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
        } else {
            steps.skip(6, "PRESCRIPTION_CLAIM", "no prescription token / non-medication profile");
        }

        // ── Steps 7–8: financial clearance + payment — OF-B9/OF-B10 scope ──
        steps.skip(7, "FINANCIAL_CLEARANCE", "OF-B9 wave scope — not evaluated, recorded honestly");
        steps.skip(8, "PAYMENT_EXECUTION", "OF-B10 wave scope — nothing charged in this wave");

        // ── §13.4: mandatory controlled-register write before commitment completes ──
        if (request.isControlled()) {
            boolean allWritten = true;
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
            if (!allWritten) {
                releaseReservations(reserved, inbound);
                markProjectionsReleased(selection.getSelectionId());
                releaseClaimIfAny(selection, inbound);
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
        steps.pass(10, "COMMITMENT_RECORDED", null, null);
        OfferStateMachine.assertTransition(offer.getStatus(), OfferStatus.COMMITTED);
        offer.setStatus(OfferStatus.COMMITTED);
        offerRepository.save(offer);
        MarketplaceRequestStateMachine.assertTransition(
                request.getStatus(), MarketplaceRequestStatus.COMMITTED);
        request.setStatus(MarketplaceRequestStatus.COMMITTED);
        requestRepository.save(request);

        // ── Step 10: losing offers released ──
        for (FulfillmentOfferEntity other : offerRepository.findByRequestId(requestId)) {
            if (!other.getOfferId().equals(offerId) && other.getStatus() == OfferStatus.ACTIVE) {
                other.setStatus(OfferStatus.NOT_SELECTED);
                other.setStatusReason("ANOTHER_OFFER_COMMITTED");
                offerRepository.save(other);
            }
        }
        steps.pass(11, "LOSING_OFFERS_RELEASED", null, null);

        // ── Step 11: fulfilment dispatch — honest PARTIAL seam ──
        // The existing msika-flow order path is cart/payment-shaped and is not trivially
        // wireable to an RFO commitment; the dispatch seam (pharmacy dispense episode /
        // provider work order, idempotent on selectionId per RC-8) is a follow-on build.
        // Recorded PARTIAL, never faked.
        steps.partial(12, "FULFILMENT_DISPATCH",
                "dispatch seam not wired this wave; idempotent on selectionId when built (RC-8)");

        selection.setStepLogJson(steps.toJson(objectMapper));
        selectionRepository.save(selection);

        // ── Step 12: events ──
        publishSelectionEvent(selection, request, offer, "SELECTION_COMMITTED", CODE_COMMITTED);
        log.info("Selection committed: selection={} request={} offer={} vendor={}",
                selection.getSelectionId(), requestId, offerId, offer.getVendorId());
        return new CommitResult(selection, true, CODE_COMMITTED, false);
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
            payload.put("priceTotal", offer.getPriceTotal() != null ? offer.getPriceTotal().toPlainString() : null);
            payload.put("currency", offer.getCurrency());
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
