package zw.gov.mohcc.impilo.nhume.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.nhume.api.dto.AssignDeliveryRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.ChainOfCustodyRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.CreateDeliveryRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.DeliveryResponse;
import zw.gov.mohcc.impilo.nhume.api.dto.ProofRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.StatusChangeRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.TrackingUpdateRequest;
import zw.gov.mohcc.impilo.nhume.core.NhumeDeliveryService;
import zw.gov.mohcc.impilo.nhume.domain.ChainOfCustodyEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryAssignmentEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryItemEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryNotificationEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPackageEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryProofEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatusEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryTrackingEventEntity;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST surface for Nhume deliveries.
 *
 * <p>Endpoints are split into two parallel namespaces sharing the same handlers:
 *
 * <ul>
 *   <li><b>/api/v1/nhume/*</b> — public API consumed by web shells and mobile apps</li>
 *   <li><b>/internal/v1/nhume/*</b> — internal call-path used by other Impilo services</li>
 * </ul>
 *
 * <p>Both share the v1.2 mandatory headers enforced by the tech-companion filter.
 */
@RestController
@RequestMapping
public class NhumeDeliveryController {

    private static final Logger log = LoggerFactory.getLogger(NhumeDeliveryController.class);

    private final NhumeDeliveryService service;
    private final TrustLayerGuard trust;

    public NhumeDeliveryController(NhumeDeliveryService service, TrustLayerGuard trust) {
        this.service = service;
        this.trust = trust;
    }

    // ─── List & detail ──────────────────────────────────────────────────────

    @GetMapping({"/api/v1/nhume/deliveries", "/internal/v1/nhume/deliveries"})
    public ResponseEntity<?> listDeliveries(
            @RequestParam(required = false) String status,
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(name = "delivery_type", required = false) String deliveryType,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RequestContext ctx = RequestContextHolder.require();
        Page<DeliveryRequestEntity> pageResult = service.listDeliveries(
                UUID.fromString(ctx.tenantId()), status, facilityId, deliveryType, priority,
                page, Math.min(size, 200));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", pageResult.getContent().stream().map(this::toResponse).toList());
        body.put("page", page);
        body.put("size", pageResult.getSize());
        body.put("total_elements", pageResult.getTotalElements());
        body.put("has_more", pageResult.hasNext());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/nhume/deliveries/{id}", "/internal/v1/nhume/deliveries/{id}"})
    public ResponseEntity<?> getDelivery(@PathVariable UUID id) {
        RequestContext ctx = RequestContextHolder.require();
        Optional<DeliveryRequestEntity> delivery = service.getDelivery(id);
        if (delivery.isEmpty()) return notFound(ctx, "Delivery not found: " + id);
        return ResponseEntity.ok(toResponse(delivery.get()));
    }

    @GetMapping({"/api/v1/nhume/deliveries/{id}/timeline",
            "/internal/v1/nhume/deliveries/{id}/timeline"})
    public ResponseEntity<?> timeline(@PathVariable UUID id) {
        if (service.getDelivery(id).isEmpty())
            return notFound(RequestContextHolder.require(), "Delivery not found: " + id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delivery_id", id);
        body.put("status_events", service.getStatusTimeline(id)
                .stream().map(this::toStatusEvent).toList());
        body.put("tracking_events", service.getTrackingTimeline(id)
                .stream().map(this::toTrackingEvent).toList());
        body.put("assignments", service.getAssignments(id)
                .stream().map(this::toAssignment).toList());
        body.put("proofs", service.getProofs(id).stream().map(this::toProof).toList());
        body.put("chain_of_custody", service.getCustody(id)
                .stream().map(this::toCustody).toList());
        body.put("notifications", service.getNotifications(id)
                .stream().map(this::toNotification).toList());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/api/v1/nhume/deliveries/{id}/items",
            "/internal/v1/nhume/deliveries/{id}/items"})
    public ResponseEntity<?> items(@PathVariable UUID id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", service.getItems(id).stream().map(this::toItem).toList());
        body.put("packages", service.getPackages(id).stream().map(this::toPackage).toList());
        return ResponseEntity.ok(body);
    }

    // ─── Create & lifecycle ─────────────────────────────────────────────────

    @PostMapping({"/api/v1/nhume/deliveries", "/internal/v1/nhume/deliveries"})
    public ResponseEntity<?> create(@Valid @RequestBody CreateDeliveryRequest request,
                                     HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "delivery.create");
        trust.requirePurposeOfUse(actor, "delivery.create");
        String idempotency = http.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        log.info("Nhume create delivery [source={}, type={}, priority={}] corr={}",
                request.requestSource(), request.deliveryType(), request.priority(),
                ctx.correlationId());
        DeliveryRequestEntity d = service.createDelivery(UUID.fromString(ctx.tenantId()),
                ctx.podId(), ctx.correlationId(), idempotency, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(d));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/submit",
            "/internal/v1/nhume/deliveries/{id}/submit"})
    public ResponseEntity<?> submit(@PathVariable UUID id,
                                     @RequestBody(required = false) StatusChangeRequest body,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.submit");
        return ResponseEntity.ok(toResponse(service.submit(id, body, actor)));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/approve",
            "/internal/v1/nhume/deliveries/{id}/approve"})
    public ResponseEntity<?> approve(@PathVariable UUID id,
                                      @RequestBody(required = false) StatusChangeRequest body,
                                      HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.approve");
        return ResponseEntity.ok(toResponse(service.approve(id, body, actor)));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/reject",
            "/internal/v1/nhume/deliveries/{id}/reject"})
    public ResponseEntity<?> reject(@PathVariable UUID id,
                                     @RequestBody(required = false) StatusChangeRequest body,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.reject");
        return ResponseEntity.ok(toResponse(service.reject(id, body, actor)));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/cancel",
            "/internal/v1/nhume/deliveries/{id}/cancel"})
    public ResponseEntity<?> cancel(@PathVariable UUID id,
                                     @RequestBody(required = false) StatusChangeRequest body,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.cancel");
        return ResponseEntity.ok(toResponse(service.cancel(id, body, actor)));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/fail",
            "/internal/v1/nhume/deliveries/{id}/fail"})
    public ResponseEntity<?> fail(@PathVariable UUID id,
                                   @RequestBody(required = false) StatusChangeRequest body,
                                   HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.fail");
        return ResponseEntity.ok(toResponse(service.markFailed(id, body, actor)));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/return",
            "/internal/v1/nhume/deliveries/{id}/return"})
    public ResponseEntity<?> returnDelivery(@PathVariable UUID id,
                                             @RequestBody(required = false) StatusChangeRequest body,
                                             HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.return");
        return ResponseEntity.ok(toResponse(service.returnDelivery(id, body, actor)));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/start",
            "/internal/v1/nhume/deliveries/{id}/start"})
    public ResponseEntity<?> startTransit(@PathVariable UUID id,
                                           @RequestBody(required = false) StatusChangeRequest body,
                                           HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.start_transit");
        return ResponseEntity.ok(toResponse(service.startTransit(id, body, actor)));
    }

    // ─── Assignment ─────────────────────────────────────────────────────────

    @PostMapping({"/api/v1/nhume/deliveries/{id}/assign",
            "/internal/v1/nhume/deliveries/{id}/assign"})
    public ResponseEntity<?> assign(@PathVariable UUID id,
                                     @Valid @RequestBody AssignDeliveryRequest request,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.assign");
        String idempotency = http.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        DeliveryAssignmentEntity a = service.assign(id, request, actor, idempotency);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAssignment(a));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/reassign",
            "/internal/v1/nhume/deliveries/{id}/reassign"})
    public ResponseEntity<?> reassign(@PathVariable UUID id,
                                       @Valid @RequestBody AssignDeliveryRequest request,
                                       HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.reassign");
        String idempotency = http.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        DeliveryAssignmentEntity a = service.assign(id, request, actor, idempotency);
        return ResponseEntity.ok(toAssignment(a));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/accept",
            "/internal/v1/nhume/deliveries/{id}/accept"})
    public ResponseEntity<?> accept(@PathVariable UUID id, HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.accept");
        DeliveryAssignmentEntity a = service.accept(id, actor);
        return ResponseEntity.ok(toAssignment(a));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/decline",
            "/internal/v1/nhume/deliveries/{id}/decline"})
    public ResponseEntity<?> decline(@PathVariable UUID id,
                                      @RequestBody(required = false) StatusChangeRequest body,
                                      HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.decline");
        DeliveryAssignmentEntity a = service.decline(id, body != null ? body.reason() : null, actor);
        return ResponseEntity.ok(toAssignment(a));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/pickup",
            "/internal/v1/nhume/deliveries/{id}/pickup"})
    public ResponseEntity<?> pickup(@PathVariable UUID id,
                                     @RequestBody(required = false) StatusChangeRequest body,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.pickup");
        return ResponseEntity.ok(toResponse(service.confirmPickup(id, body, actor)));
    }

    // ─── Tracking ───────────────────────────────────────────────────────────

    @PostMapping({"/api/v1/nhume/deliveries/{id}/location",
            "/internal/v1/nhume/deliveries/{id}/location"})
    public ResponseEntity<?> recordLocation(@PathVariable UUID id,
                                             @RequestBody TrackingUpdateRequest request,
                                             HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.location_update");
        DeliveryTrackingEventEntity e = service.recordTracking(id, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTrackingEvent(e));
    }

    @GetMapping({"/api/v1/nhume/deliveries/{id}/tracking",
            "/internal/v1/nhume/deliveries/{id}/tracking"})
    public ResponseEntity<?> tracking(@PathVariable UUID id) {
        RequestContext ctx = RequestContextHolder.require();
        Optional<DeliveryRequestEntity> delivery = service.getDelivery(id);
        if (delivery.isEmpty()) return notFound(ctx, "Delivery not found: " + id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delivery_id", id);
        body.put("status", delivery.get().getStatus());
        body.put("origin_label", delivery.get().getOriginLabel());
        body.put("destination_label", delivery.get().getDestinationLabel());
        body.put("sla_due_at", delivery.get().getSlaDueAt());
        body.put("tracking_events", service.getTrackingTimeline(id)
                .stream().map(this::toTrackingEvent).toList());
        body.put("last_location", lastLocationOf(service.getTrackingTimeline(id)));
        body.put("eta_seconds", computeEta(delivery.get(), service.getTrackingTimeline(id)));
        body.put("map_provider", service.ndila().mapProvider());
        return ResponseEntity.ok(body);
    }

    // ─── Proof / custody ────────────────────────────────────────────────────

    @PostMapping({"/api/v1/nhume/deliveries/{id}/proof",
            "/internal/v1/nhume/deliveries/{id}/proof"})
    public ResponseEntity<?> proof(@PathVariable UUID id,
                                    @Valid @RequestBody ProofRequest request,
                                    HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.proof");
        DeliveryProofEntity p = service.captureProof(id, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(toProof(p));
    }

    @PostMapping({"/api/v1/nhume/deliveries/{id}/custody",
            "/internal/v1/nhume/deliveries/{id}/custody"})
    public ResponseEntity<?> custody(@PathVariable UUID id,
                                      @Valid @RequestBody ChainOfCustodyRequest request,
                                      HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.custody");
        ChainOfCustodyEventEntity e = service.recordCustody(id, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCustody(e));
    }

    @DeleteMapping({"/api/v1/nhume/deliveries/{id}",
            "/internal/v1/nhume/deliveries/{id}"})
    public ResponseEntity<?> deleteDraft(@PathVariable UUID id,
                                          HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = capture(http, "delivery.cancel");
        service.cancel(id, new StatusChangeRequest("Soft-deleted by request", null), actor);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private TrustLayerGuard.ActorContext capture(HttpServletRequest http, String action) {
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, action);
        trust.requirePurposeOfUse(actor, action);
        return actor;
    }

    private ResponseEntity<?> notFound(RequestContext ctx, String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorEnvelope.of("NOT_FOUND", message,
                        ctx.requestId(), ctx.correlationId()));
    }

    private DeliveryResponse toResponse(DeliveryRequestEntity d) {
        return new DeliveryResponse(d.getDeliveryId(), d.getTenantId(), d.getReferenceCode(),
                d.getStatus(), d.getPriority(), d.getDeliveryType(), d.getRequestSource(),
                d.getOriginLabel(), d.getDestinationLabel(), d.getRecipientName(),
                d.getRecipientPhone(), d.getRequestingFacilityId(),
                d.getTelehealthSessionRef(), d.getMarketplaceOrderRef(),
                d.getPolicyId(), d.getSlaId(), d.getSlaDueAt(),
                d.isColdChainRequired(), d.isControlledItem(),
                d.isChainOfCustodyRequired(), d.isDemo(),
                d.getCreatedAt(), d.getUpdatedAt(), d.getSubmittedAt(),
                d.getDeliveredAt(), d.getFailedAt());
    }

    private Map<String, Object> toStatusEvent(DeliveryStatusEventEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("previous_status", e.getPreviousStatus());
        m.put("new_status", e.getNewStatus());
        m.put("actor_id", e.getActorId());
        m.put("actor_type", e.getActorType());
        m.put("reason", e.getReason());
        m.put("created_at", e.getCreatedAt());
        return m;
    }

    private Map<String, Object> toTrackingEvent(DeliveryTrackingEventEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("captured_at", e.getCapturedAt());
        m.put("event_kind", e.getEventKind());
        m.put("lat", e.getLat());
        m.put("lng", e.getLng());
        m.put("accuracy_m", e.getAccuracyM());
        m.put("courier_id", e.getCourierId());
        m.put("asset_id", e.getAssetId());
        m.put("source", e.getSource());
        return m;
    }

    private Map<String, Object> toAssignment(DeliveryAssignmentEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assignment_id", a.getAssignmentId());
        m.put("delivery_id", a.getDeliveryId());
        m.put("courier_id", a.getCourierId());
        m.put("asset_id", a.getAssetId());
        m.put("integration_provider", a.getIntegrationProvider());
        m.put("integration_ref", a.getIntegrationRef());
        m.put("mode_category", a.getModeCategory());
        m.put("assignment_kind", a.getAssignmentKind());
        m.put("status", a.getStatus());
        m.put("assigned_at", a.getAssignedAt());
        m.put("accepted_at", a.getAcceptedAt());
        m.put("declined_at", a.getDeclinedAt());
        m.put("decline_reason", a.getDeclineReason());
        return m;
    }

    private Map<String, Object> toProof(DeliveryProofEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("proof_id", p.getProofId());
        m.put("proof_stage", p.getProofStage());
        m.put("proof_method", p.getProofMethod());
        m.put("captured_by", p.getCapturedBy());
        m.put("captured_at", p.getCapturedAt());
        m.put("geofence_match", p.getGeofenceMatch());
        m.put("locker_code", p.getLockerCode());
        m.put("verified", p.isVerified());
        return m;
    }

    private Map<String, Object> toCustody(ChainOfCustodyEventEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("coc_id", c.getCocId());
        m.put("package_id", c.getPackageId());
        m.put("sequence_no", c.getSequenceNo());
        m.put("event_kind", c.getEventKind());
        m.put("custody_holder", c.getCustodyHolder());
        m.put("custody_location", c.getCustodyLocation());
        m.put("seal_id", c.getSealId());
        m.put("temperature_c", c.getTemperatureC());
        m.put("verification", c.getVerification());
        m.put("actor_id", c.getActorId());
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    private Map<String, Object> toItem(DeliveryItemEntity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item_id", i.getItemId());
        m.put("sequence_no", i.getSequenceNo());
        m.put("item_kind", i.getItemKind());
        m.put("item_ref", i.getItemRef());
        m.put("description", i.getDescription());
        m.put("quantity", i.getQuantity());
        m.put("unit_of_measure", i.getUnitOfMeasure());
        m.put("cold_chain", i.isColdChain());
        m.put("controlled", i.isControlled());
        return m;
    }

    private Map<String, Object> toPackage(DeliveryPackageEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("package_id", p.getPackageId());
        m.put("package_code", p.getPackageCode());
        m.put("package_type", p.getPackageType());
        m.put("seal_id", p.getSealId());
        m.put("cold_chain", p.isColdChain());
        m.put("fragile", p.isFragile());
        return m;
    }

    private Map<String, Object> toNotification(DeliveryNotificationEventEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event_code", n.getEventCode());
        m.put("channel", n.getChannel());
        m.put("recipient_ref", n.getRecipientRef());
        m.put("template_code", n.getTemplateCode());
        m.put("delivery_state", n.getDeliveryState());
        m.put("provider_ref", n.getProviderRef());
        m.put("dispatched_at", n.getDispatchedAt());
        return m;
    }

    private Map<String, Object> lastLocationOf(List<DeliveryTrackingEventEntity> tracking) {
        return tracking.stream()
                .filter(t -> t.getLat() != null && t.getLng() != null)
                .findFirst()
                .map(t -> {
                    Map<String, Object> loc = new LinkedHashMap<>();
                    loc.put("lat", t.getLat());
                    loc.put("lng", t.getLng());
                    loc.put("captured_at", t.getCapturedAt());
                    return loc;
                })
                .orElse(null);
    }

    private Long computeEta(DeliveryRequestEntity d, List<DeliveryTrackingEventEntity> tracking) {
        if (d.getDestinationLat() == null || d.getDestinationLng() == null) return null;
        return tracking.stream()
                .filter(t -> t.getLat() != null && t.getLng() != null)
                .findFirst()
                .map(t -> service.ndila().etaSeconds(t.getLat(), t.getLng(),
                        d.getDestinationLat(), d.getDestinationLng(), "CAR"))
                .orElse(null);
    }
}
