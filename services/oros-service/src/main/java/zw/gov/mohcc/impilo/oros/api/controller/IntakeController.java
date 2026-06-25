package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.oros.api.dto.OrderItemDto;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.api.dto.PlaceOrderRequest;
import zw.gov.mohcc.impilo.oros.core.OrderStateMachine;
import zw.gov.mohcc.impilo.oros.core.OrderSubmissionService;
import zw.gov.mohcc.impilo.oros.core.RoutingEngine;
import zw.gov.mohcc.impilo.oros.core.SlaService;
import zw.gov.mohcc.impilo.oros.core.WorkstepEngine;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Non-electronic / external order intake (spec §6, acceptance criteria C & D): paper requests and
 * manually-entered external/walk-in orders. Each creates a draft with the appropriate
 * {@link RequestSource}, then submits it (reserving an accession for imaging and initializing the
 * imaging workflow) — provenance is preserved on the order.
 */
@RestController
public class IntakeController {

    private static final Logger log = LoggerFactory.getLogger(IntakeController.class);

    private final OrderStateMachine stateMachine;
    private final OrderSubmissionService submissionService;
    private final RoutingEngine routingEngine;
    private final WorkstepEngine workstepEngine;
    private final SlaService slaService;

    public IntakeController(OrderStateMachine stateMachine,
                            OrderSubmissionService submissionService,
                            RoutingEngine routingEngine,
                            WorkstepEngine workstepEngine,
                            SlaService slaService) {
        this.stateMachine = stateMachine;
        this.submissionService = submissionService;
        this.routingEngine = routingEngine;
        this.workstepEngine = workstepEngine;
        this.slaService = slaService;
    }

    /** Capture a scanned paper request as an order (source=PAPER). */
    @PostMapping("/v1/intake/paper")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> paperIntake(
            @Valid @RequestBody PlaceOrderRequest request) {
        return intake(request, RequestSource.PAPER);
    }

    /** Manually enter an external / walk-in order (source from body, defaulting to EXTERNAL). */
    @PostMapping("/v1/orders/external-manual")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> externalManual(
            @Valid @RequestBody PlaceOrderRequest request) {
        RequestSource source = request.requestSource() != null ? request.requestSource() : RequestSource.EXTERNAL;
        return intake(request, source);
    }

    private ResponseEntity<ApiResponse<OrderSummaryDto>> intake(PlaceOrderRequest request, RequestSource source) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        OrderEntity draft = stateMachine.createDraft(
                ctx.facilityId(), request.patientCpid(), request.orderType(), request.priority(),
                source, request.ziboOrderCode(), request.encounterRef(), request.clinicalNotes(),
                request.referringProviderId(), request.referringProviderName(),
                request.scheduledAt(), request.safetyJson(), toItemData(request.items()));

        OrderEntity order = submissionService.submit(draft.getOrderId());

        routingEngine.routeOrder(order);
        workstepEngine.createWorkstepsForOrder(order);
        slaService.startTimer(order.getOrderId(), "OVERALL", 0);

        log.info("Intake order created: orderId={}, source={}", order.getOrderId(), source);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
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
}
