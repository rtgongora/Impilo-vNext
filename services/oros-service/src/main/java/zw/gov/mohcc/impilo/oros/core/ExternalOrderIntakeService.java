package zw.gov.mohcc.impilo.oros.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;

/**
 * Single intake path for externally-originated orders (FHIR ServiceRequest-inbound, HL7
 * ORM-inbound): idempotent on the external ref, then create a {@code DRAFT} with
 * {@code requestSource=EXTERNAL}, submit it (reserves accession / inits imaging workflow), and
 * orchestrate routing + worksteps + SLA — exactly like the internal intake flow.
 */
@Service
public class ExternalOrderIntakeService {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderIntakeService.class);

    private final OrderStateMachine stateMachine;
    private final OrderRepository orderRepository;
    private final OrderSubmissionService submissionService;
    private final RoutingEngine routingEngine;
    private final WorkstepEngine workstepEngine;
    private final SlaService slaService;

    public ExternalOrderIntakeService(OrderStateMachine stateMachine,
                                      OrderRepository orderRepository,
                                      OrderSubmissionService submissionService,
                                      RoutingEngine routingEngine,
                                      WorkstepEngine workstepEngine,
                                      SlaService slaService) {
        this.stateMachine = stateMachine;
        this.orderRepository = orderRepository;
        this.submissionService = submissionService;
        this.routingEngine = routingEngine;
        this.workstepEngine = workstepEngine;
        this.slaService = slaService;
    }

    @Transactional
    public OrderEntity create(ExternalOrderIntake input) {
        TrustContext ctx = TrustContextHolder.require();

        if (input.externalRef() != null) {
            var existing = orderRepository.findByTenantIdAndExternalOrderRef(ctx.tenantId(), input.externalRef());
            if (existing.isPresent()) {
                log.info("External order already ingested (idempotent): externalRef={}, orderId={}",
                        input.externalRef(), existing.get().getOrderId());
                return existing.get();
            }
        }
        if (input.patientCpid() == null || input.patientCpid().isBlank()) {
            throw new IllegalArgumentException("external order is missing a patient CPID");
        }

        List<OrderStateMachine.OrderItemData> items = List.of(
                OrderStateMachine.OrderItemData.lab(input.code(),
                        input.displayName() != null ? input.displayName() : input.code(), 1, null, null, null));

        OrderEntity draft = stateMachine.createDraft(
                ctx.facilityId(), input.patientCpid(), input.orderType(), input.priority(),
                RequestSource.EXTERNAL, null, null, null, input.clinicalNotes(),
                null, null, null, null, items);
        draft.setExternalOrderRef(input.externalRef());
        orderRepository.save(draft);

        OrderEntity order = submissionService.submit(draft.getOrderId());
        routingEngine.routeOrder(order);
        workstepEngine.createWorkstepsForOrder(order);
        slaService.startTimer(order.getOrderId(), "OVERALL", 0);

        log.info("External order ingested: externalRef={}, orderId={}, type={}, cpid={}",
                input.externalRef(), order.getOrderId(), input.orderType(), input.patientCpid());
        return order;
    }
}
