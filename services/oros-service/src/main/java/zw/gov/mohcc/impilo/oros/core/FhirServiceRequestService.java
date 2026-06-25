package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;

/**
 * FHIR ServiceRequest-inbound adapter: map an external FHIR R4 {@code ServiceRequest} into an OROS
 * order ({@code requestSource=EXTERNAL}), then submit + route it like any other intake.
 *
 * <p>Idempotent on the ServiceRequest's external identifier (re-sends resolve to the same order).
 * Patient identity is taken from {@code subject} (Patient/CPID or an identifier value); production
 * deployments should cross-walk an external MRN → Impilo CPID via VITO before this point.</p>
 */
@Service
public class FhirServiceRequestService {

    private static final Logger log = LoggerFactory.getLogger(FhirServiceRequestService.class);

    private final OrderStateMachine stateMachine;
    private final OrderRepository orderRepository;
    private final OrderSubmissionService submissionService;
    private final RoutingEngine routingEngine;
    private final WorkstepEngine workstepEngine;
    private final SlaService slaService;

    public FhirServiceRequestService(OrderStateMachine stateMachine,
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
    public OrderEntity ingest(JsonNode sr) {
        TrustContext ctx = TrustContextHolder.require();

        String externalRef = extractExternalRef(sr);
        if (externalRef != null) {
            var existing = orderRepository.findByTenantIdAndExternalOrderRef(ctx.tenantId(), externalRef);
            if (existing.isPresent()) {
                log.info("FHIR ServiceRequest already ingested (idempotent): externalRef={}, orderId={}",
                        externalRef, existing.get().getOrderId());
                return existing.get();
            }
        }

        String cpid = extractCpid(sr);
        if (cpid == null || cpid.isBlank()) {
            throw new IllegalArgumentException("ServiceRequest.subject is missing a patient reference/identifier");
        }
        OrderType type = mapOrderType(sr);
        OrderPriority priority = mapPriority(sr);
        String code = extractCode(sr);
        String notes = text(sr.path("note").path(0), "text");

        List<OrderStateMachine.OrderItemData> items =
                List.of(OrderStateMachine.OrderItemData.lab(code, code, 1, null, null, null));

        OrderEntity draft = stateMachine.createDraft(
                ctx.facilityId(), cpid, type, priority, RequestSource.EXTERNAL,
                null, null, notes, null, null, null, null, items);
        draft.setExternalOrderRef(externalRef);
        orderRepository.save(draft);

        OrderEntity order = submissionService.submit(draft.getOrderId());
        routingEngine.routeOrder(order);
        workstepEngine.createWorkstepsForOrder(order);
        slaService.startTimer(order.getOrderId(), "OVERALL", 0);

        log.info("FHIR ServiceRequest ingested: externalRef={}, orderId={}, type={}, cpid={}",
                externalRef, order.getOrderId(), type, cpid);
        return order;
    }

    // ── parsing helpers ──

    private String extractExternalRef(JsonNode sr) {
        JsonNode id0 = sr.path("identifier").path(0);
        String value = text(id0, "value");
        if (value == null) {
            return null;
        }
        String system = text(id0, "system");
        return system != null ? system + "|" + value : value;
    }

    private String extractCpid(JsonNode sr) {
        JsonNode subject = sr.path("subject");
        String ref = text(subject, "reference");
        if (ref != null && ref.startsWith("Patient/")) {
            return ref.substring("Patient/".length());
        }
        String idValue = text(subject.path("identifier"), "value");
        return idValue != null ? idValue : ref;
    }

    private OrderType mapOrderType(JsonNode sr) {
        String cat = text(sr.path("category").path(0).path("coding").path(0), "code");
        if (cat != null) {
            String c = cat.toLowerCase();
            if (c.contains("imag") || c.contains("rad") || c.equals("363679005")) {
                return OrderType.IMAGING;
            }
            if (c.contains("lab")) {
                return OrderType.LAB;
            }
        }
        return OrderType.LAB;
    }

    private OrderPriority mapPriority(JsonNode sr) {
        String p = text(sr, "priority");
        if (p == null) {
            return OrderPriority.ROUTINE;
        }
        return switch (p.toLowerCase()) {
            case "stat" -> OrderPriority.STAT;
            case "urgent" -> OrderPriority.URGENT;
            case "asap" -> OrderPriority.ASAP;
            default -> OrderPriority.ROUTINE;
        };
    }

    private String extractCode(JsonNode sr) {
        JsonNode coding = sr.path("code").path("coding").path(0);
        String code = text(coding, "code");
        return code != null ? code : "UNSPECIFIED";
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
