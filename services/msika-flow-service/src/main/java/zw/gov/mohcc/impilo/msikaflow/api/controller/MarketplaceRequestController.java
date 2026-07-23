package zw.gov.mohcc.impilo.msikaflow.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.api.dto.MarketplaceDtos.*;
import zw.gov.mohcc.impilo.msikaflow.core.CommitmentService;
import zw.gov.mohcc.impilo.msikaflow.core.MarketplaceRequestService;
import zw.gov.mohcc.impilo.msikaflow.core.PublishedSnapshotBuilder;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SelectionEntity;

import java.util.List;
import java.util.UUID;

/**
 * OF-B4/OF-B5/OF-B6/OF-B11 — the regulated request-for-offer API
 * ({@code /v1/marketplace-requests}).
 *
 * <p>Every view returned here is PII-free by construction: the request's line
 * truth is the §11.8-minimised published snapshot; the prescription token is
 * write-only and never echoed.</p>
 */
@RestController
@RequestMapping("/v1/marketplace-requests")
public class MarketplaceRequestController {

    private final MarketplaceRequestService requestService;
    private final CommitmentService commitmentService;
    private final ObjectMapper objectMapper;

    public MarketplaceRequestController(MarketplaceRequestService requestService,
                                        CommitmentService commitmentService,
                                        ObjectMapper objectMapper) {
        this.requestService = requestService;
        this.commitmentService = commitmentService;
        this.objectMapper = objectMapper;
    }

    // ── request lifecycle ────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<RequestView>> create(
            @Valid @RequestBody CreateRequestDto dto, HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        String actorId = TrustHeaderExtractor.actorId(http);
        List<PublishedSnapshotBuilder.RequestLine> lines = dto.lines().stream()
                .map(l -> new PublishedSnapshotBuilder.RequestLine(
                        l.lineRef(), l.itemCode(), l.codingSystem(), l.quantity(), l.unit(),
                        l.controlled(), l.coldChain(),
                        l.substitutionAllowed() == null || l.substitutionAllowed()))
                .toList();
        MarketplaceRequestEntity request = requestService.createFromOrder(tenantId, actorId,
                new MarketplaceRequestService.CreateCommand(
                        dto.orosOrderId(), dto.orosOrderVersionId(), dto.profile(),
                        dto.publicationMode(), dto.coarseZone(), dto.urgency(), lines,
                        dto.invitedVendorIds(), dto.prescriptionToken()),
                http);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(view(request), TrustHeaderExtractor.correlationId(http)));
    }

    @PostMapping("/{requestId}/publish")
    public ResponseEntity<ApiResponse<PublishResponse>> publish(
            @PathVariable String requestId, HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        String actorId = TrustHeaderExtractor.actorId(http);
        MarketplaceRequestService.PublishOutcome outcome =
                requestService.publish(requestId, tenantId, actorId, http);
        return ResponseEntity.ok(ApiResponse.ok(
                new PublishResponse(view(outcome.request()), outcome.invitations().size(), outcome.refusals()),
                TrustHeaderExtractor.correlationId(http)));
    }

    /** Vendor/patient view — published snapshot + windows/status only. */
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<RequestView>> get(
            @PathVariable String requestId, HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        MarketplaceRequestEntity request = requestService.getRequest(requestId, tenantId);
        return ResponseEntity.ok(ApiResponse.ok(view(request), TrustHeaderExtractor.correlationId(http)));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<RequestView>> cancel(
            @PathVariable String requestId, @RequestBody(required = false) CancelDto dto,
            HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        MarketplaceRequestEntity request = requestService.cancel(requestId, tenantId,
                dto != null ? dto.reason() : null);
        return ResponseEntity.ok(ApiResponse.ok(view(request), TrustHeaderExtractor.correlationId(http)));
    }

    // ── vendor offers ────────────────────────────────────────────────────

    @PostMapping("/{requestId}/offers")
    public ResponseEntity<ApiResponse<OfferView>> submitOffer(
            @PathVariable String requestId, @Valid @RequestBody SubmitOfferDto dto,
            HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        List<MarketplaceRequestService.OfferLineCommand> lines = dto.lines().stream()
                .map(l -> new MarketplaceRequestService.OfferLineCommand(
                        l.requestLineRef(), l.itemCode(), l.quantity(), l.unitPrice(),
                        l.substitutionProposed()))
                .toList();
        FulfillmentOfferEntity offer = requestService.submitOffer(requestId, tenantId,
                new MarketplaceRequestService.SubmitOfferCommand(dto.vendorId(), dto.priceTotal(),
                        dto.currency(), dto.fulfillmentWindowHours(), dto.ttlMinutes(), lines),
                http);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                offerView(offer, List.of(), null), TrustHeaderExtractor.correlationId(http)));
    }

    @PostMapping("/offers/{offerId}/withdraw")
    public ResponseEntity<ApiResponse<OfferView>> withdrawOffer(
            @PathVariable String offerId, @RequestBody(required = false) WithdrawDto dto,
            HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        FulfillmentOfferEntity offer = requestService.withdrawOffer(offerId, tenantId,
                dto != null ? dto.reason() : null);
        return ResponseEntity.ok(ApiResponse.ok(
                offerView(offer, List.of(), null), TrustHeaderExtractor.correlationId(http)));
    }

    // ── patient comparison + selection ───────────────────────────────────

    @GetMapping("/{requestId}/offers")
    public ResponseEntity<ApiResponse<List<OfferView>>> listOffers(
            @PathVariable String requestId, HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        List<OfferView> views = requestService.listOffers(requestId, tenantId).stream()
                .map(r -> offerView(r.offer(), r.lines(), r.rankedBecause()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(views, TrustHeaderExtractor.correlationId(http)));
    }

    /** RC-1: Idempotency-Key mandatory; replays return the first attempt's result. */
    @PostMapping("/{requestId}/select")
    public ResponseEntity<ApiResponse<SelectionView>> select(
            @PathVariable String requestId, @Valid @RequestBody SelectDto dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest http) {
        UUID tenantId = TrustHeaderExtractor.tenantId(http);
        String actorId = TrustHeaderExtractor.actorId(http);
        CommitmentService.CommitResult result = commitmentService.commit(
                requestId, dto.offerId(), tenantId, actorId, idempotencyKey, http);
        // Replay → 200; fresh commit → 201; a coded revalidation failure (RC-2/3/6/7)
        // is an honest outcome, not a transport error → 200 with committed=false.
        HttpStatus status = !result.replayed() && result.committed() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(
                selectionView(result), TrustHeaderExtractor.correlationId(http)));
    }

    // ── view mapping ─────────────────────────────────────────────────────

    private RequestView view(MarketplaceRequestEntity request) {
        return new RequestView(
                request.getRequestId(),
                request.getOrosOrderId(),
                request.getOrosOrderVersionId(),
                request.getProfile().name(),
                request.getPublicationMode().name(),
                request.getStatus().name(),
                request.isControlled(),
                request.getCoarseZone(),
                request.getUrgency(),
                request.getOfferWindowEndsAt(),
                request.getSelectionWindowEndsAt(),
                parse(request.getPublishedLinesJson()));
    }

    private OfferView offerView(FulfillmentOfferEntity offer, List<FulfillmentOfferLineEntity> lines,
                                List<String> rankedBecause) {
        List<OfferLineView> lineViews = lines.stream()
                .map(l -> new OfferLineView(l.getOfferLineId(), l.getRequestLineRef(), l.getItemCode(),
                        l.getQuantity(), l.getUnitPrice(), l.getStockGrade().name(),
                        l.isSubstitutionProposed()))
                .toList();
        return new OfferView(offer.getOfferId(), offer.getRequestId(), offer.getVendorId(),
                offer.getStatus().name(), offer.getStatusReason(), offer.getPriceTotal(),
                offer.getCurrency(), offer.getFulfillmentWindowHours(), offer.getTtlExpiresAt(),
                lineViews, rankedBecause);
    }

    private SelectionView selectionView(CommitmentService.CommitResult result) {
        SelectionEntity s = result.selection();
        return new SelectionView(s.getSelectionId(), s.getRequestId(), s.getOfferId(),
                s.getStatus().name(), result.outcomeCode(), result.committed(), result.replayed(),
                s.getPrescriptionClaimId(), s.getCommittedAt(), parse(s.getStepLogJson()));
    }

    private JsonNode parse(String json) {
        try {
            return json != null ? objectMapper.readTree(json) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
