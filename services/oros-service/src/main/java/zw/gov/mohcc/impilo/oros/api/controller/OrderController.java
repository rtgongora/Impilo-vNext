package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.CancelRequest;
import zw.gov.mohcc.impilo.oros.api.dto.ImagingTransitionRequest;
import zw.gov.mohcc.impilo.oros.api.dto.LinkStudyRequest;
import zw.gov.mohcc.impilo.oros.api.dto.NoteRequest;
import zw.gov.mohcc.impilo.oros.api.dto.OrderItemDto;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.api.dto.PlaceOrderRequest;
import zw.gov.mohcc.impilo.oros.api.dto.PrintableRequest;
import zw.gov.mohcc.impilo.oros.api.dto.RouteDto;
import zw.gov.mohcc.impilo.oros.api.dto.RouteRequest;
import zw.gov.mohcc.impilo.oros.api.dto.ScheduleRequest;
import zw.gov.mohcc.impilo.oros.core.*;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.RoutingEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.SlaTimerEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for clinical order management.
 *
 * <p>Two creation paths are supported:</p>
 * <ul>
 *   <li><b>Immediate place</b> ({@code POST /v1/orders}) — legacy/quick path that creates and
 *       routes a {@code PLACED} order in one call (lab/pharmacy style).</li>
 *   <li><b>Draft → submit</b> ({@code POST /v1/orders/draft} then {@code POST
 *       /v1/orders/{id}/submit}) — the diagnostic/imaging journey: a draft is composed, then
 *       submitted, which reserves an accession (imaging), resolves the referring provider, and
 *       initializes the imaging workflow before routing.</li>
 * </ul>
 *
 * <p>The fine-grained imaging lifecycle (schedule/arrive/release and the generic transition) is
 * driven through {@link ImagingWorkflowService}; the coarse canonical status machine is left
 * untouched.</p>
 */
@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderStateMachine stateMachine;
    private final OrderSubmissionService submissionService;
    private final ImagingWorkflowService imagingWorkflowService;
    private final OrderQueryService orderQueryService;
    private final DiagnosticShareService diagnosticShareService;
    private final zw.gov.mohcc.impilo.oros.integration.dicom.MwlPublisherRouter mwlPublisherRouter;
    private final RoutingEngine routingEngine;
    private final WorkstepEngine workstepEngine;
    private final SlaService slaService;

    public OrderController(OrderStateMachine stateMachine,
                           OrderSubmissionService submissionService,
                           ImagingWorkflowService imagingWorkflowService,
                           OrderQueryService orderQueryService,
                           DiagnosticShareService diagnosticShareService,
                           zw.gov.mohcc.impilo.oros.integration.dicom.MwlPublisherRouter mwlPublisherRouter,
                           RoutingEngine routingEngine,
                           WorkstepEngine workstepEngine,
                           SlaService slaService) {
        this.stateMachine = stateMachine;
        this.submissionService = submissionService;
        this.imagingWorkflowService = imagingWorkflowService;
        this.orderQueryService = orderQueryService;
        this.diagnosticShareService = diagnosticShareService;
        this.mwlPublisherRouter = mwlPublisherRouter;
        this.routingEngine = routingEngine;
        this.workstepEngine = workstepEngine;
        this.slaService = slaService;
    }

    /**
     * Place a new clinical order immediately (legacy/quick path).
     *
     * <p>Orchestrates the full placement flow: create (PLACED) → route → worksteps → SLA timer.</p>
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderSummaryDto>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        OrderEntity order = stateMachine.placeOrder(
                ctx.facilityId(),
                request.patientCpid(),
                request.orderType(),
                request.priority(),
                request.ziboOrderCode(),
                request.encounterRef(),
                request.clinicalNotes(),
                toItemData(request.items()));

        orchestratePlacement(order);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Create a diagnostic/imaging order in {@code DRAFT} state (not yet routed).
     */
    @PostMapping("/draft")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> createDraft(
            @Valid @RequestBody PlaceOrderRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        OrderEntity order = stateMachine.createDraft(
                ctx.facilityId(),
                request.patientCpid(),
                request.orderType(),
                request.priority(),
                request.requestSource(),
                request.ziboOrderCode(),
                request.encounterRef(),
                request.clinicalNotes(),
                request.referringProviderId(),
                request.referringProviderName(),
                request.scheduledAt(),
                request.safetyJson(),
                toItemData(request.items()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Update a {@code DRAFT} order (fields + items replaced wholesale).
     */
    @PutMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> updateDraft(
            @PathVariable String orderId,
            @Valid @RequestBody PlaceOrderRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = stateMachine.updateDraft(
                orderId,
                request.priority(),
                request.requestSource(),
                request.ziboOrderCode(),
                request.encounterRef(),
                request.clinicalNotes(),
                request.referringProviderId(),
                request.referringProviderName(),
                request.scheduledAt(),
                request.safetyJson(),
                toItemData(request.items()));

        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Submit a draft order: reserve accession (imaging), resolve referring provider, initialize
     * the imaging workflow, transition {@code DRAFT → PLACED}, then route + worksteps + SLA.
     */
    @PostMapping("/{orderId}/submit")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> submitOrder(@PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = submissionService.submit(orderId);
        orchestratePlacement(order);

        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Assign a routing destination to an order (requester referral/routing action).
     */
    @PostMapping("/{orderId}/route")
    public ResponseEntity<ApiResponse<RouteDto>> routeOrder(
            @PathVariable String orderId,
            @Valid @RequestBody RouteRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        RoutingEntity route = routingEngine.assignDestination(orderId,
                new RoutingEngine.RouteDestination(
                        request.destinationType(),
                        request.destinationFacilityId(),
                        request.destinationDepartmentId(),
                        request.destinationServicePointId(),
                        request.destinationProviderId(),
                        request.destinationName(),
                        request.expectedReturnMethod(),
                        request.routeExpiry()));

        return ResponseEntity.ok(ApiResponse.ok(RouteDto.from(route), correlationId));
    }

    /**
     * Schedule an imaging order's acquisition/appointment (drives imaging state to SCHEDULED).
     */
    @PostMapping("/{orderId}/schedule")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> scheduleOrder(
            @PathVariable String orderId,
            @Valid @RequestBody ScheduleRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = imagingWorkflowService.schedule(orderId, request.scheduledAt(), request.note());
        // Publish a DICOM MWL item for the scheduled procedure step (gated; OFF by default).
        // Modality is resolved from the order's items so the SPS carries the real machine type
        // (falls back to "OT" inside the dataset builder only when no item declares one).
        mwlPublisherRouter.publish(order, imagingWorkflowService.firstItemModality(order));
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Mark a scheduled imaging patient as arrived (drives imaging state to ARRIVED).
     */
    @PostMapping("/{orderId}/arrive")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> arriveOrder(
            @PathVariable String orderId,
            @RequestBody(required = false) NoteRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = imagingWorkflowService.transition(
                orderId, ImagingWorkflowState.ARRIVED, note(request));
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Release an imaging result to requesters (drives imaging state to RELEASED).
     */
    @PostMapping("/{orderId}/release")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> releaseOrder(
            @PathVariable String orderId,
            @RequestBody(required = false) NoteRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = imagingWorkflowService.transition(
                orderId, ImagingWorkflowState.RELEASED, note(request));
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Generic guarded imaging-workflow transition (used by fulfilment/reporting waves for
     * receive/accept/reject/start/complete/etc.).
     */
    @PostMapping("/{orderId}/imaging/transition")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> transitionImaging(
            @PathVariable String orderId,
            @Valid @RequestBody ImagingTransitionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = imagingWorkflowService.transition(orderId, request.target(), request.reason());
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * List/search orders within the tenant, filtered by client, requester, status, and type.
     *
     * <p>Backs order tracking and requester views — e.g. {@code GET
     * /v1/orders?client=CPID-1&status=PLACED&type=IMAGING}.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> listOrders(
            @RequestParam(name = "client", required = false) String client,
            @RequestParam(name = "requester", required = false) String requester,
            @RequestParam(name = "status", required = false) zw.gov.mohcc.impilo.oros.domain.OrderStatus status,
            @RequestParam(name = "type", required = false) zw.gov.mohcc.impilo.oros.domain.OrderType type,
            @RequestParam(name = "encounter", required = false) String encounter) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<OrderSummaryDto> orders = orderQueryService.search(client, requester, status, type, encounter).stream()
                .map(OrderSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId));
    }

    /**
     * Imaging-team worklist for the current facility, filtered by fine-grained imaging state.
     *
     * <p>e.g. {@code GET /v1/orders/imaging-worklist?states=RECEIVED,ACCEPTED}. Defaults to the
     * active pre-report states when none are supplied.</p>
     */
    @GetMapping("/imaging-worklist")
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> imagingWorklist(
            @RequestParam(name = "states", required = false) List<ImagingWorkflowState> states) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<OrderSummaryDto> orders = orderQueryService.imagingWorklist(states).stream()
                .map(OrderSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId));
    }

    /** Accept an imaging order (imaging state -> ACCEPTED). */
    @PostMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> acceptOrder(
            @PathVariable String orderId, @RequestBody(required = false) NoteRequest request) {
        return imagingActionResponse(orderId, ImagingWorkflowState.ACCEPTED, request);
    }

    /** Reject an imaging order with a reason (imaging state -> REJECTED). */
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> rejectOrder(
            @PathVariable String orderId, @RequestBody(required = false) NoteRequest request) {
        return imagingActionResponse(orderId, ImagingWorkflowState.REJECTED, request);
    }

    /** Return an imaging order for clarification with a reason. */
    @PostMapping("/{orderId}/clarify")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> clarifyOrder(
            @PathVariable String orderId, @RequestBody(required = false) NoteRequest request) {
        return imagingActionResponse(orderId, ImagingWorkflowState.RETURNED_FOR_CLARIFICATION, request);
    }

    /** Start the imaging examination (imaging state -> IN_PROGRESS). */
    @PostMapping("/{orderId}/start")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> startOrder(
            @PathVariable String orderId, @RequestBody(required = false) NoteRequest request) {
        return imagingActionResponse(orderId, ImagingWorkflowState.IN_PROGRESS, request);
    }

    /** Complete the imaging examination (imaging state -> PERFORMED). */
    @PostMapping("/{orderId}/complete")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> completeOrder(
            @PathVariable String orderId, @RequestBody(required = false) NoteRequest request) {
        return imagingActionResponse(orderId, ImagingWorkflowState.PERFORMED, request);
    }

    /** Issue a printable, OTP-protected QR for a patient-carried order (criterion D). */
    @PostMapping("/{orderId}/printable")
    public ResponseEntity<ApiResponse<zw.gov.mohcc.impilo.oros.integration.ShareSlipClient.ShareLinkRef>> printable(
            @PathVariable String orderId, @RequestBody(required = false) PrintableRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        int expiryHours = request != null && request.expiryHours() != null ? request.expiryHours() : 168;
        int maxClaims = request != null && request.maxClaims() != null ? request.maxClaims() : 1;
        var ref = diagnosticShareService.printable(orderId, expiryHours, maxClaims);
        return ResponseEntity.ok(ApiResponse.ok(ref, correlationId));
    }

    /** Link a PACS/DICOM study to the order (imaging state -> IMAGES_LINKED). */
    @PostMapping("/{orderId}/link-study")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> linkStudy(
            @PathVariable String orderId, @Valid @RequestBody LinkStudyRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        OrderEntity order = imagingWorkflowService.linkStudy(
                orderId, request.studyUid(), request.viewerUrl(), request.accessionNumber());
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Get a single order by its ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> getOrder(@PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = stateMachine.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Get all orders for a patient by CPID.
     */
    @GetMapping("/patient/{cpid}")
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> getPatientOrders(
            @PathVariable String cpid) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<OrderSummaryDto> orders = stateMachine.getPatientOrders(cpid).stream()
                .map(OrderSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId));
    }

    /**
     * Cancel an existing order.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> cancelOrder(
            @PathVariable String orderId,
            @Valid @RequestBody CancelRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = stateMachine.cancelOrder(orderId, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Route the order, generate worksteps, and start the overall SLA timer. */
    private void orchestratePlacement(OrderEntity order) {
        RoutingEntity route = routingEngine.routeOrder(order);
        List<WorkstepEntity> worksteps = workstepEngine.createWorkstepsForOrder(order);
        SlaTimerEntity timer = slaService.startTimer(order.getOrderId(), "OVERALL", 0);

        log.info("Order placed and routed: orderId={}, route={}, worksteps={}, slaTarget={}",
                order.getOrderId(), route.getRouteTarget(), worksteps.size(), timer.getTargetAt());
    }

    private static List<OrderStateMachine.OrderItemData> toItemData(List<OrderItemDto> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(dto -> new OrderStateMachine.OrderItemData(
                        dto.code(), dto.displayName(), dto.quantity(),
                        dto.instructions(), dto.specimenType(), dto.bodySite(),
                        dto.modality(), dto.laterality(), dto.contrast(), dto.procedureCode()))
                .collect(Collectors.toList());
    }

    private static String note(NoteRequest request) {
        return request != null ? request.note() : null;
    }

    /** Drive a guarded imaging transition from a convenience endpoint and wrap the response. */
    private ResponseEntity<ApiResponse<OrderSummaryDto>> imagingActionResponse(
            String orderId, ImagingWorkflowState target, NoteRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        OrderEntity order = imagingWorkflowService.transition(orderId, target, note(request));
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }
}
