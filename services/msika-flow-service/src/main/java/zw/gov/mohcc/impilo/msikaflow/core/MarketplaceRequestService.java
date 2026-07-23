package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.InventoryClient;
import zw.gov.mohcc.impilo.msikaflow.integration.OrosClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * OF-B4 — the regulated request-for-offer lifecycle: create-from-order
 * (version-pinned, PII-minimised), publish (eligibility-gated invitations,
 * §13.4 controlled gating), vendor offers (OF-B6), comparison view with
 * ranked-because labels, cancellation, and the TTL/window sweeps.
 *
 * <p>Selection/commitment lives in {@link CommitmentService}.</p>
 */
@Service
public class MarketplaceRequestService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceRequestService.class);

    /** Wave OF-A supported publication modes (§11.2 subset; the rest await OD-12/policy encoding). */
    private static final Set<PublicationMode> SUPPORTED_MODES = Set.of(
            PublicationMode.OPEN, PublicationMode.INVITED,
            PublicationMode.GEO_RADIUS, PublicationMode.PATIENT_REQUESTED_CHECK);

    private final MarketplaceRequestRepository requestRepository;
    private final MarketplaceInvitationRepository invitationRepository;
    private final FulfillmentOfferRepository offerRepository;
    private final FulfillmentOfferLineRepository offerLineRepository;
    private final VendorProfileRepository vendorRepository;
    private final EventOutboxRepository outboxRepository;
    private final EligibilityService eligibilityService;
    private final OrosClient orosClient;
    private final InventoryClient inventoryClient;
    private final ObjectMapper objectMapper;

    private final long offerWindowMinutes;
    private final long selectionWindowMinutes;
    private final long defaultOfferTtlMinutes;
    private final boolean verifyOrderVersion;

    public MarketplaceRequestService(MarketplaceRequestRepository requestRepository,
                                     MarketplaceInvitationRepository invitationRepository,
                                     FulfillmentOfferRepository offerRepository,
                                     FulfillmentOfferLineRepository offerLineRepository,
                                     VendorProfileRepository vendorRepository,
                                     EventOutboxRepository outboxRepository,
                                     EligibilityService eligibilityService,
                                     OrosClient orosClient,
                                     InventoryClient inventoryClient,
                                     ObjectMapper objectMapper,
                                     @Value("${msika-flow.marketplace.offer-window-minutes:120}") long offerWindowMinutes,
                                     @Value("${msika-flow.marketplace.selection-window-minutes:120}") long selectionWindowMinutes,
                                     @Value("${msika-flow.marketplace.default-offer-ttl-minutes:60}") long defaultOfferTtlMinutes,
                                     @Value("${msika-flow.marketplace.verify-order-version:true}") boolean verifyOrderVersion) {
        this.requestRepository = requestRepository;
        this.invitationRepository = invitationRepository;
        this.offerRepository = offerRepository;
        this.offerLineRepository = offerLineRepository;
        this.vendorRepository = vendorRepository;
        this.outboxRepository = outboxRepository;
        this.eligibilityService = eligibilityService;
        this.orosClient = orosClient;
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
        this.offerWindowMinutes = offerWindowMinutes;
        this.selectionWindowMinutes = selectionWindowMinutes;
        this.defaultOfferTtlMinutes = defaultOfferTtlMinutes;
        this.verifyOrderVersion = verifyOrderVersion;
    }

    // ── create (DRAFT) ───────────────────────────────────────────────────

    public record CreateCommand(
            String orosOrderId,
            String orosOrderVersionId,
            MarketplaceProfile profile,
            PublicationMode publicationMode,
            String coarseZone,
            String urgency,
            List<PublishedSnapshotBuilder.RequestLine> lines,
            List<String> invitedVendorIds,
            String prescriptionToken
    ) {}

    @Transactional
    public MarketplaceRequestEntity createFromOrder(UUID tenantId, String actorId,
                                                    CreateCommand cmd, HttpServletRequest inbound) {
        if (cmd.orosOrderId() == null || cmd.orosOrderId().isBlank()
                || cmd.orosOrderVersionId() == null || cmd.orosOrderVersionId().isBlank()) {
            throw new IllegalArgumentException("orosOrderId and orosOrderVersionId (the version pin) are required");
        }
        if (cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new IllegalArgumentException("at least one coded line is required");
        }
        if (!SUPPORTED_MODES.contains(cmd.publicationMode())) {
            throw new IllegalArgumentException("PUBLICATION_MODE_UNSUPPORTED: mode "
                    + cmd.publicationMode() + " is not supported in this wave");
        }
        boolean controlled = cmd.lines().stream().anyMatch(PublishedSnapshotBuilder.RequestLine::controlled);
        if (controlled && cmd.publicationMode() == PublicationMode.OPEN) {
            // §13.4.1 — controlled lines are NEVER open-broadcast.
            throw new IllegalArgumentException(
                    "CONTROLLED_OPEN_BROADCAST_FORBIDDEN: controlled lines publish only via INVITED-class modes");
        }
        if ((cmd.publicationMode() == PublicationMode.INVITED
                || cmd.publicationMode() == PublicationMode.PATIENT_REQUESTED_CHECK)
                && (cmd.invitedVendorIds() == null || cmd.invitedVendorIds().isEmpty())) {
            throw new IllegalArgumentException("invitedVendorIds is required for mode " + cmd.publicationMode());
        }
        if (cmd.publicationMode() == PublicationMode.PATIENT_REQUESTED_CHECK
                && cmd.invitedVendorIds().size() != 1) {
            throw new IllegalArgumentException("PATIENT_REQUESTED_CHECK is a single-invitation round");
        }
        if (cmd.publicationMode() == PublicationMode.GEO_RADIUS
                && (cmd.coarseZone() == null || cmd.coarseZone().isBlank())) {
            throw new IllegalArgumentException("coarseZone is required for GEO_RADIUS publication");
        }

        // OF-B1 version pin — verified against OROS, fail-closed on an unreachable order plane.
        if (verifyOrderVersion) {
            Optional<Boolean> exists = orosClient.orderVersionExists(
                    cmd.orosOrderId(), cmd.orosOrderVersionId(), inbound);
            if (exists.isEmpty()) {
                throw new UpstreamUnavailableException(
                        "OROS unavailable — cannot verify order version pin " + cmd.orosOrderVersionId());
            }
            if (!exists.get()) {
                throw new IllegalArgumentException("ORDER_VERSION_UNKNOWN: version "
                        + cmd.orosOrderVersionId() + " does not exist on order " + cmd.orosOrderId());
            }
        }

        MarketplaceRequestEntity request = new MarketplaceRequestEntity();
        request.setRequestId(UlidGenerator.generate());
        request.setTenantId(tenantId);
        request.setOrosOrderId(cmd.orosOrderId());
        request.setOrosOrderVersionId(cmd.orosOrderVersionId());
        request.setProfile(cmd.profile());
        request.setPublicationMode(cmd.publicationMode());
        request.setStatus(MarketplaceRequestStatus.DRAFT);
        request.setControlled(controlled);
        request.setCoarseZone(cmd.coarseZone());
        request.setUrgency(cmd.urgency());
        request.setPublishedLinesJson(PublishedSnapshotBuilder.build(objectMapper,
                cmd.profile().name(), cmd.coarseZone(), cmd.urgency(), cmd.lines()));
        request.setInvitedVendorIds(JsonSupport.toJsonSafe(objectMapper, cmd.invitedVendorIds(), null));
        request.setPrescriptionToken(cmd.prescriptionToken());
        request.setRequestedBy(actorId);
        requestRepository.save(request);
        log.info("Marketplace request created: id={} order={} pin={} mode={} controlled={}",
                request.getRequestId(), cmd.orosOrderId(), cmd.orosOrderVersionId(),
                cmd.publicationMode(), controlled);
        return request;
    }

    // ── publish ──────────────────────────────────────────────────────────

    public record PublishOutcome(MarketplaceRequestEntity request,
                                 List<MarketplaceInvitationEntity> invitations,
                                 Map<String, String> refusals /* vendorId -> refusal code */) {}

    @Transactional
    public PublishOutcome publish(String requestId, UUID tenantId, String actorId, HttpServletRequest inbound) {
        MarketplaceRequestEntity request = getRequest(requestId, tenantId);
        MarketplaceRequestStateMachine.assertTransition(request.getStatus(), MarketplaceRequestStatus.PUBLISHED);

        List<VendorProfileEntity> candidates = resolveCandidates(request, tenantId);
        List<MarketplaceInvitationEntity> invitations = new ArrayList<>();
        Map<String, String> refusals = new LinkedHashMap<>();

        for (VendorProfileEntity vendor : candidates) {
            // §13.4 controlled gating: the invitation set is filtered to controlled-authority
            // vendors BEFORE the full evaluation, so controlled lines are never exposed at all.
            if (request.isControlled() && !eligibilityService.hasControlledAuthority(vendor)) {
                refusals.put(vendor.getVendorId(), EligibilityService.CODE_CONTROLLED_AUTHORITY_MISSING);
                continue;
            }
            EligibilityService.EligibilityResult result =
                    eligibilityService.evaluate(vendor, request, inbound);
            if (!result.eligible()) {
                // §4.3: never a silent drop — the refusal is recorded and surfaced.
                refusals.put(vendor.getVendorId(), result.firstRefusalCode());
                continue;
            }
            MarketplaceInvitationEntity invitation = new MarketplaceInvitationEntity();
            invitation.setInvitationId(UlidGenerator.generate());
            invitation.setTenantId(tenantId);
            invitation.setRequestId(request.getRequestId());
            invitation.setVendorId(vendor.getVendorId());
            invitation.setStatus(InvitationStatus.SENT);
            invitation.setEligibilitySnapshotJson(eligibilityService.toSnapshotJson(result));
            invitation.setSentAt(OffsetDateTime.now());
            invitationRepository.save(invitation);
            invitations.add(invitation);
        }

        if (invitations.isEmpty()) {
            // §9.3 PUBLISHED guard: eligible-vendor set non-empty (else exception).
            throw new IllegalStateException("NO_ELIGIBLE_VENDORS: no vendor passed the six-precondition "
                    + "gate for request " + requestId + " (refusals: " + refusals + ")");
        }

        OffsetDateTime now = OffsetDateTime.now();
        request.setOfferWindowEndsAt(now.plusMinutes(offerWindowMinutes));
        request.setSelectionWindowEndsAt(now.plusMinutes(offerWindowMinutes + selectionWindowMinutes));
        request.setStatus(MarketplaceRequestStatus.PUBLISHED);
        requestRepository.save(request);

        // Invitations delivered synchronously in this wave → COLLECTING_OFFERS immediately.
        MarketplaceRequestStateMachine.assertTransition(request.getStatus(), MarketplaceRequestStatus.COLLECTING_OFFERS);
        request.setStatus(MarketplaceRequestStatus.COLLECTING_OFFERS);
        requestRepository.save(request);

        publishRequestEvent(request, "REQUEST_PUBLISHED", Map.of(
                "invitationCount", invitations.size(),
                "refusalCount", refusals.size()));
        log.info("Marketplace request published: id={} invitations={} refusals={}",
                requestId, invitations.size(), refusals.size());
        return new PublishOutcome(request, invitations, refusals);
    }

    private List<VendorProfileEntity> resolveCandidates(MarketplaceRequestEntity request, UUID tenantId) {
        return switch (request.getPublicationMode()) {
            case INVITED, PATIENT_REQUESTED_CHECK -> namedVendors(request, tenantId);
            case GEO_RADIUS -> allTenantVendors(tenantId).stream()
                    .filter(v -> eligibilityService.coversZone(v, request.getCoarseZone()))
                    .toList();
            case OPEN -> allTenantVendors(tenantId);
            default -> throw new IllegalArgumentException(
                    "PUBLICATION_MODE_UNSUPPORTED: " + request.getPublicationMode());
        };
    }

    private List<VendorProfileEntity> namedVendors(MarketplaceRequestEntity request, UUID tenantId) {
        List<VendorProfileEntity> vendors = new ArrayList<>();
        try {
            JsonNode ids = objectMapper.readTree(
                    request.getInvitedVendorIds() != null ? request.getInvitedVendorIds() : "[]");
            for (JsonNode id : ids) {
                vendorRepository.findById(id.asText())
                        .filter(v -> tenantId.equals(v.getTenantId()))
                        .ifPresent(vendors::add);
            }
        } catch (Exception e) {
            log.warn("Unparseable invitedVendorIds for request {}: {}", request.getRequestId(), e.getMessage());
        }
        return vendors;
    }

    private List<VendorProfileEntity> allTenantVendors(UUID tenantId) {
        return vendorRepository.findByTenantId(tenantId, PageRequest.of(0, 500)).getContent();
    }

    // ── vendor offers (OF-B6) ────────────────────────────────────────────

    public record OfferLineCommand(String requestLineRef, String itemCode, int quantity,
                                   BigDecimal unitPrice, boolean substitutionProposed) {}

    public record SubmitOfferCommand(String vendorId, BigDecimal priceTotal, String currency,
                                     Integer fulfillmentWindowHours, Long ttlMinutes,
                                     List<OfferLineCommand> lines) {}

    @Transactional
    public FulfillmentOfferEntity submitOffer(String requestId, UUID tenantId,
                                              SubmitOfferCommand cmd, HttpServletRequest inbound) {
        MarketplaceRequestEntity request = getRequest(requestId, tenantId);
        if (request.getStatus() != MarketplaceRequestStatus.COLLECTING_OFFERS
                && request.getStatus() != MarketplaceRequestStatus.OFFERS_AVAILABLE) {
            throw new IllegalStateException("OFFER_WINDOW_CLOSED: request " + requestId
                    + " is not collecting offers (status " + request.getStatus() + ")");
        }
        if (request.getOfferWindowEndsAt() != null
                && request.getOfferWindowEndsAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("OFFER_WINDOW_CLOSED: offer window ended at "
                    + request.getOfferWindowEndsAt());
        }
        if (cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new IllegalArgumentException("an offer requires at least one line");
        }
        if (cmd.priceTotal() == null || cmd.priceTotal().signum() < 0) {
            throw new IllegalArgumentException("priceTotal must be non-negative");
        }
        MarketplaceInvitationEntity invitation = invitationRepository
                .findFirstByRequestIdAndVendorId(requestId, cmd.vendorId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "VENDOR_NOT_INVITED: vendor " + cmd.vendorId() + " holds no invitation for request " + requestId));
        if (invitation.getStatus() == InvitationStatus.EXPIRED
                || invitation.getStatus() == InvitationStatus.DECLINED) {
            throw new IllegalStateException("INVITATION_NOT_OPEN: invitation status " + invitation.getStatus());
        }

        VendorProfileEntity vendor = vendorRepository.findById(cmd.vendorId())
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + cmd.vendorId()));

        // §11.3 — re-evaluated at offer time; fail-closed with structured refusal.
        EligibilityService.EligibilityResult eligibility =
                eligibilityService.evaluate(vendor, request, inbound);
        if (!eligibility.eligible()) {
            throw new IllegalStateException("OFFER_REFUSED_" + eligibility.firstRefusalCode()
                    + ": vendor failed eligibility at offer time");
        }

        OffsetDateTime now = OffsetDateTime.now();
        FulfillmentOfferEntity offer = new FulfillmentOfferEntity();
        offer.setOfferId(UlidGenerator.generate());
        offer.setTenantId(tenantId);
        offer.setRequestId(requestId);
        offer.setInvitationId(invitation.getInvitationId());
        offer.setVendorId(cmd.vendorId());
        offer.setStatus(OfferStatus.SUBMITTED);
        offer.setPriceTotal(cmd.priceTotal());
        offer.setCurrency(cmd.currency() != null ? cmd.currency() : "ZWG");
        offer.setFulfillmentWindowHours(cmd.fulfillmentWindowHours());
        offer.setTtlExpiresAt(now.plusMinutes(
                cmd.ttlMinutes() != null && cmd.ttlMinutes() > 0 ? cmd.ttlMinutes() : defaultOfferTtlMinutes));
        offer.setEligibilitySnapshotJson(eligibilityService.toSnapshotJson(eligibility));
        offer.setSubmittedAt(now);
        offerRepository.save(offer);

        Optional<EligibilityService.DuraSource> duraSource = eligibilityService.duraSource(vendor);
        for (OfferLineCommand lineCmd : cmd.lines()) {
            FulfillmentOfferLineEntity line = new FulfillmentOfferLineEntity();
            line.setOfferLineId(UlidGenerator.generate());
            line.setTenantId(tenantId);
            line.setOfferId(offer.getOfferId());
            line.setRequestLineRef(lineCmd.requestLineRef());
            line.setItemCode(lineCmd.itemCode());
            line.setQuantity(lineCmd.quantity());
            line.setUnitPrice(lineCmd.unitPrice());
            line.setSubstitutionProposed(lineCmd.substitutionProposed());
            line.setStockGrade(gradeLine(duraSource, lineCmd, inbound));
            offerLineRepository.save(line);
        }

        // SUBMITTED → ACTIVE (content validation passed inline in this wave).
        OfferStateMachine.assertTransition(offer.getStatus(), OfferStatus.ACTIVE);
        offer.setStatus(OfferStatus.ACTIVE);
        offerRepository.save(offer);

        invitation.setStatus(InvitationStatus.OFFERED);
        invitationRepository.save(invitation);

        if (request.getStatus() == MarketplaceRequestStatus.COLLECTING_OFFERS) {
            MarketplaceRequestStateMachine.assertTransition(
                    request.getStatus(), MarketplaceRequestStatus.OFFERS_AVAILABLE);
            request.setStatus(MarketplaceRequestStatus.OFFERS_AVAILABLE);
            requestRepository.save(request);
        }

        publishRequestEvent(request, "OFFER_SUBMITTED", Map.of(
                "offerId", offer.getOfferId(),
                "vendorId", offer.getVendorId(),
                "priceTotal", offer.getPriceTotal().toPlainString(),
                "currency", offer.getCurrency()));
        log.info("Offer submitted: offer={} request={} vendor={}", offer.getOfferId(), requestId, cmd.vendorId());
        return offer;
    }

    /**
     * §8E stock-truth grading: VERIFIED only when DURA answered and
     * available (on-hand − reserved) covers the quantity; any failure or a
     * vendor without a DURA source degrades honestly to REPORTED.
     */
    private StockGrade gradeLine(Optional<EligibilityService.DuraSource> duraSource,
                                 OfferLineCommand line, HttpServletRequest inbound) {
        if (duraSource.isEmpty()) {
            return StockGrade.REPORTED;
        }
        return inventoryClient.availability(duraSource.get().facilityId(), duraSource.get().storeId(),
                        line.itemCode(), inbound)
                .filter(a -> a.available() >= line.quantity())
                .map(a -> StockGrade.VERIFIED)
                .orElse(StockGrade.REPORTED);
    }

    @Transactional
    public FulfillmentOfferEntity withdrawOffer(String offerId, UUID tenantId, String reason) {
        FulfillmentOfferEntity offer = offerRepository.findByOfferIdAndTenantId(offerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));
        // §9.4 guard: not while SELECTED-in-commit — the transition map enforces it.
        OfferStateMachine.assertTransition(offer.getStatus(), OfferStatus.WITHDRAWN);
        offer.setStatus(OfferStatus.WITHDRAWN);
        offer.setStatusReason(reason != null ? reason : "VENDOR_WITHDRAWN");
        offerRepository.save(offer);

        requestRepository.findById(offer.getRequestId()).ifPresent(request ->
                publishRequestEvent(request, "OFFER_WITHDRAWN", Map.of(
                        "offerId", offer.getOfferId(), "vendorId", offer.getVendorId())));
        return offer;
    }

    // ── comparison view (basic ranked-because set) ───────────────────────

    public record RankedOffer(FulfillmentOfferEntity offer, List<FulfillmentOfferLineEntity> lines,
                              List<String> rankedBecause) {}

    @Transactional(readOnly = true)
    public List<RankedOffer> listOffers(String requestId, UUID tenantId) {
        MarketplaceRequestEntity request = getRequest(requestId, tenantId);
        List<FulfillmentOfferEntity> active = offerRepository
                .findByRequestIdAndStatus(requestId, OfferStatus.ACTIVE);
        if (active.isEmpty()) {
            return List.of();
        }
        Set<String> requestLineRefs = requestLineRefs(request);
        BigDecimal cheapest = active.stream()
                .map(FulfillmentOfferEntity::getPriceTotal)
                .min(Comparator.naturalOrder())
                .orElse(null);

        List<RankedOffer> ranked = new ArrayList<>();
        for (FulfillmentOfferEntity offer : active) {
            List<FulfillmentOfferLineEntity> lines = offerLineRepository.findByOfferId(offer.getOfferId());
            List<String> labels = new ArrayList<>();
            labels.add("SUITABLE"); // passed the eligibility gate — the base label
            if (cheapest != null && offer.getPriceTotal().compareTo(cheapest) == 0) {
                labels.add("CHEAPEST");
            }
            if (isNearest(offer, request, tenantId)) {
                labels.add("NEAREST");
            }
            if (coversAllLines(lines, requestLineRefs)) {
                labels.add("COMPLETE");
            }
            ranked.add(new RankedOffer(offer, lines, labels));
        }
        // Declared default sort: clinical completeness first, then price — never price alone (§4.5).
        ranked.sort(Comparator
                .comparing((RankedOffer r) -> !r.rankedBecause().contains("COMPLETE"))
                .thenComparing(r -> r.offer().getPriceTotal()));
        return ranked;
    }

    private boolean isNearest(FulfillmentOfferEntity offer, MarketplaceRequestEntity request, UUID tenantId) {
        return vendorRepository.findById(offer.getVendorId())
                .filter(v -> tenantId.equals(v.getTenantId()))
                .map(v -> eligibilityService.coversZone(v, request.getCoarseZone()))
                .orElse(false);
    }

    private boolean coversAllLines(List<FulfillmentOfferLineEntity> lines, Set<String> requestLineRefs) {
        if (requestLineRefs.isEmpty()) {
            return false;
        }
        Set<String> offered = new HashSet<>();
        boolean substituted = false;
        for (FulfillmentOfferLineEntity line : lines) {
            offered.add(line.getRequestLineRef());
            substituted |= line.isSubstitutionProposed();
        }
        return !substituted && offered.containsAll(requestLineRefs);
    }

    private Set<String> requestLineRefs(MarketplaceRequestEntity request) {
        Set<String> refs = new HashSet<>();
        try {
            JsonNode snapshot = objectMapper.readTree(request.getPublishedLinesJson());
            for (JsonNode line : snapshot.path("lines")) {
                refs.add(line.path("lineRef").asText());
            }
        } catch (Exception e) {
            log.warn("Unparseable snapshot for request {}: {}", request.getRequestId(), e.getMessage());
        }
        return refs;
    }

    // ── cancel ───────────────────────────────────────────────────────────

    @Transactional
    public MarketplaceRequestEntity cancel(String requestId, UUID tenantId, String reason) {
        MarketplaceRequestEntity request = getRequest(requestId, tenantId);
        MarketplaceRequestStateMachine.assertTransition(request.getStatus(), MarketplaceRequestStatus.CANCELLED);
        request.setStatus(MarketplaceRequestStatus.CANCELLED);
        request.setCancelReason(reason != null ? reason : "REQUESTER_CANCELLED");
        requestRepository.save(request);

        // §9.3 CANCELLED guard: live offers released, reason coded.
        for (FulfillmentOfferEntity offer : offerRepository.findByRequestId(requestId)) {
            if (offer.getStatus() == OfferStatus.ACTIVE || offer.getStatus() == OfferStatus.SUBMITTED) {
                offer.setStatus(OfferStatus.NOT_SELECTED);
                offer.setStatusReason("REQUEST_CANCELLED");
                offerRepository.save(offer);
            }
        }
        publishRequestEvent(request, "REQUEST_CANCELLED", Map.of("reason", request.getCancelReason()));
        return request;
    }

    // ── TTL / window sweeps (§9.3/§9.4 timers) ───────────────────────────

    @Transactional
    public int sweepExpiredOffers(OffsetDateTime now) {
        List<FulfillmentOfferEntity> lapsed =
                offerRepository.findByStatusAndTtlExpiresAtBefore(OfferStatus.ACTIVE, now);
        for (FulfillmentOfferEntity offer : lapsed) {
            offer.setStatus(OfferStatus.EXPIRED);
            offer.setStatusReason("TTL_LAPSED");
            offerRepository.save(offer);
            requestRepository.findById(offer.getRequestId()).ifPresent(request ->
                    publishRequestEvent(request, "OFFER_EXPIRED", Map.of("offerId", offer.getOfferId())));
        }
        return lapsed.size();
    }

    @Transactional
    public int sweepOfferWindows(OffsetDateTime now) {
        List<MarketplaceRequestEntity> due = requestRepository.findByStatusInAndOfferWindowEndsAtBefore(
                List.of(MarketplaceRequestStatus.PUBLISHED, MarketplaceRequestStatus.COLLECTING_OFFERS), now);
        for (MarketplaceRequestEntity request : due) {
            boolean anyActive = !offerRepository
                    .findByRequestIdAndStatus(request.getRequestId(), OfferStatus.ACTIVE).isEmpty();
            MarketplaceRequestStatus target = anyActive
                    ? MarketplaceRequestStatus.OFFERS_AVAILABLE
                    : MarketplaceRequestStatus.FAILED_NO_OFFERS;
            MarketplaceRequestStateMachine.assertTransition(request.getStatus(), target);
            request.setStatus(target);
            requestRepository.save(request);
            if (target == MarketplaceRequestStatus.FAILED_NO_OFFERS) {
                publishRequestEvent(request, "REQUEST_FAILED_NO_OFFERS", Map.of());
            }
        }
        return due.size();
    }

    @Transactional
    public int sweepSelectionWindows(OffsetDateTime now) {
        List<MarketplaceRequestEntity> due = requestRepository.findByStatusInAndSelectionWindowEndsAtBefore(
                List.of(MarketplaceRequestStatus.OFFERS_AVAILABLE, MarketplaceRequestStatus.SELECTION_PENDING), now);
        for (MarketplaceRequestEntity request : due) {
            MarketplaceRequestStateMachine.assertTransition(request.getStatus(), MarketplaceRequestStatus.EXPIRED);
            request.setStatus(MarketplaceRequestStatus.EXPIRED);
            requestRepository.save(request);
            for (FulfillmentOfferEntity offer : offerRepository.findByRequestId(request.getRequestId())) {
                if (offer.getStatus() == OfferStatus.ACTIVE) {
                    offer.setStatus(OfferStatus.EXPIRED);
                    offer.setStatusReason("SELECTION_WINDOW_LAPSED");
                    offerRepository.save(offer);
                }
            }
            publishRequestEvent(request, "REQUEST_EXPIRED", Map.of());
        }
        return due.size();
    }

    // ── shared ───────────────────────────────────────────────────────────

    public MarketplaceRequestEntity getRequest(String requestId, UUID tenantId) {
        return requestRepository.findByRequestIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace request not found: " + requestId));
    }

    /**
     * Outbox event for a request. Payload is PII-free by construction: request
     * metadata + the already-minimised snapshot; the prescription token and any
     * actor identity are never included.
     */
    void publishRequestEvent(MarketplaceRequestEntity request, String eventType, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getRequestId());
            payload.put("tenantId", request.getTenantId().toString());
            payload.put("status", request.getStatus().name());
            payload.put("profile", request.getProfile().name());
            payload.put("publicationMode", request.getPublicationMode().name());
            payload.put("controlled", request.isControlled());
            if (request.getCoarseZone() != null) {
                payload.put("coarseZone", request.getCoarseZone());
            }
            if (request.getOfferWindowEndsAt() != null) {
                payload.put("offerWindowEndsAt", request.getOfferWindowEndsAt().toString());
            }
            payload.putAll(extra);
            if ("REQUEST_PUBLISHED".equals(eventType)) {
                payload.put("publishedLines", objectMapper.readTree(request.getPublishedLinesJson()));
            }
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("MarketplaceRequest");
            outbox.setAggregateId(request.getRequestId());
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(request.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event {} for request {}: {}",
                    eventType, request.getRequestId(), e.getMessage());
        }
    }
}
